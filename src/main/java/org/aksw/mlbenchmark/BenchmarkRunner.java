package org.aksw.mlbenchmark;

import org.aksw.mlbenchmark.config.BenchmarkConfig;
import org.aksw.mlbenchmark.mex.MEXWriter;
import org.aksw.mlbenchmark.systemrunner.CrossValidationRunner;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The main runner for the SMLBench framework, will execute one benchmark configuration (without saving of results)
 */
public class BenchmarkRunner {
	static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);
	/** the directory at BenchmarkRunner creation time */
	private final String currentDir;
	/** the url to the class files source */
	private final URL sourceDir;
	/** the detected root directory of SMLBench */
	private final String rootDir;
	/** the root BenchmarkConfig for this run */
	private final BenchmarkConfig config;
	/** the learning systems to trial */
	private final List<String> desiredSystems;
	private final int threads;
	private final Set<String> desiredLanguages = new HashSet<>();
	private final Map<String, String> systemLanguage = new HashMap<>();
	private final int folds;
	private List<String> availableLearningSystems = new LinkedList<>();
	private Map<String, LearningSystemInfo> systemInfos = new HashMap<>();
	private ExecutorService executorService;
	private Path tempDirectory;
	private long seed;
	private PropertyListConfiguration resultset = new PropertyListConfiguration();
	private BenchmarkLog benchmarkLog;
	private String mexOutputFilePath = null;

	/**
	 * create a BenchmarkRunner
	 * @param config the BenchmarkConfig
	 */
	public BenchmarkRunner(BenchmarkConfig config) {
		benchmarkLog = new BenchmarkLog();
		benchmarkLog.saveBenchmarkConfig(config);

		File file1 = new File(new File(".").getAbsolutePath());
		currentDir = (file1.getName().equals(".") ? file1.getParentFile() : file1).getAbsolutePath();
		sourceDir = BenchmarkRunner.class.getProtectionDomain().getCodeSource().getLocation();
		logger.info("source dir = " + sourceDir);
		rootDir = new File(sourceDir.getPath()).getParentFile().getParentFile().getAbsolutePath();
		logger.info("root = " + rootDir);
		File[] files = new File(getLearningSystemsDir()).listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					availableLearningSystems.add(file.getName());
				}
			}
		}
		logger.info("available learning systems:" + availableLearningSystems);
		this.config = config;
		desiredSystems = config.getLearningSystems();
		seed = config.getSeed();
		initLanguages();
		initTemp();
		folds = config.getCrossValidationFolds();
		threads = config.getThreadsCount();
		if (config.containsKey("mex.outputFile")) {
			mexOutputFilePath = config.getMexOutputFile();
		}

		if (threads == 1) {
			executorService = Executors.newSingleThreadExecutor();
		} else {
			executorService = Executors.newFixedThreadPool(threads);
		}

		benchmarkLog.saveDirs(rootDir, getLearningTasksDir(),
				getLearningSystemsDir(), tempDirectory.toAbsolutePath().toString());
	}

	/**
	 * create a BenchmarkRunner from apache commons config
	 * @param config the benchmark configuration
	 */
	public BenchmarkRunner(HierarchicalConfiguration<ImmutableNode> config) {
		this(new BenchmarkConfig(config));
	}

	/**
	 * create a BenchmarkRunner from filename
	 * @param configFilename the benchmark configuration filename to load
	 * @throws ConfigLoaderException
	 */
	public BenchmarkRunner(String configFilename) throws ConfigLoaderException {
		// load the properties file
		this(new ConfigLoader(configFilename).loadWithInfo().config());
	}

	/**
	 * @return the executor service for threaded execution
	 */
	public ExecutorService getExecutorService() {
		return executorService;
	}

	/**
	 * @return the set of languages (prolog, owl) that will be trialed
	 */
	public Set<String> getDesiredLanguages() {
		return desiredLanguages;
	}

	/**
	 * @return the working directory for all intermediate and output files
	 */
	public Path getTempDirectory() {
		return tempDirectory;
	}

	/**
	 * @return the list of systems (dllearner, aleph,...) to be trialed
	 */
	public List<String> getDesiredSystems() {
		return desiredSystems;
	}

	/**
	 * @return the random seed for reproducible folds generation
	 */
	public long getSeed() {
		return seed;
	}

	/**
	 * @return the number of folds for the CrossValidationRunner
	 */
	public int getFolds() {
		return folds;
	}

	/**
	 * create workind directory
	 */
	private void initTemp() {
		try {
			tempDirectory = Files.createTempDirectory(new File(currentDir).toPath(), "sml-temp");
			logger.info("using working directory: " + tempDirectory);
			tempDirectory.toFile().deleteOnExit();
		} catch (IOException e) {
			throw new RuntimeException("Cannot create temporary folder, terminating.", e);
		}
	}

	/**
	 * get list of languages for the desired learning systems
	 */
	private void initLanguages() {
		for (final String sys : desiredSystems) {
			LearningSystemInfo learningSystemInfo = new LearningSystemInfo(this, sys);
			String language = learningSystemInfo.getConfig().getString("language");
			desiredLanguages.add(language);
			systemLanguage.put(sys, language);
			logger.debug("language for " + sys + ": " + language);
		}

	}

	/**
	 * @param learningSystem the learning system
	 * @return directory which contains this learning system
	 */
	public String getLearningSystemDir(String learningSystem) {
		return getLearningSystemsDir() + "/" + learningSystem;
	}

	/**
	 * @return directory which contains all learning systems
	 */
	public String getLearningSystemsDir() {
		return rootDir + "/" + Constants.LEARNINGSYSTEMS;
	}

	/**
	 * @return directory which contains all learning tasks
	 */
	public String getLearningTasksDir() {
		return rootDir + "/" + Constants.LEARNINGTASKS;
	}

	/**
	 * @param learningTask the learning task
	 * @param languageType the knowledge language (prolog,owl)
	 * @return directory which contains all learning problems
	 */
	public String getLearningProblemsDir(String learningTask, String languageType) {
		return getLearningTasksDir() + "/" + learningTask + "/" + languageType + "/" + Constants.LEARNINGPROBLEMS;
	}

	/**
	 * @param scn the learning scenario
	 * @param languageType the knowledge language
	 * @return the directory which contains the examples and problem specific config for this learning problem
	 */
	public String getLearningProblemDir(Scenario scn, String languageType) {
		return getLearningProblemsDir(scn.getTask(), languageType) + "/" + scn.getProblem();
	}

	/**
	 * @return the root BenchmarkConfig which configures this framework execution
	 */
	public BenchmarkConfig getConfig() {
		return config;
	}

	/**
	 * @return the underlying commins config of the BenchmarkConfig
	 */
	public HierarchicalConfiguration<ImmutableNode> getCommonsConfig() {
		return config.getConfig();
	}

	/**
	 * helper method to replace /* in scenario config with a list of all available learning problems
	 * @param scenarios input list of scenarios
	 * @return output list of scenarios with /* replaced by sublist of all available problems in this learning task
	 */
	private List<String> expandScenarios(final Collection<String> scenarios) {
		LinkedList<String> ret = new LinkedList<>();
		for (String scn : scenarios) {
			if (scn.endsWith("/*")) { // need to expand it;
				LinkedHashSet<String> expansion = new LinkedHashSet<>();
				String[] split = scn.split("/");
				for (String lang : desiredLanguages) {
					File[] files = new File(getLearningProblemsDir(split[0], lang)).listFiles();
					if (files != null) {
						for (File f : files) {
							if (f.isDirectory()) {
								expansion.add(split[0] + "/" + f.getName());
							}
						}
					}
				}
				ret.addAll(expansion);
			} else {
				ret.add(scn);
			}
		}
		return ret;
	}

	/**
	 * the main benchmark runner will run each scenario in the config file and optionally save mex output
	 */
	public void run() {
		logger.info("benchmarking systems: " + desiredSystems);

		List<String> scenarios = expandScenarios(config.getScenarios());

		logger.info("requested scenarios: " + scenarios);
		for (final String scn : scenarios) {
			runScenario(scn);
		}

		finalizeExecutor();

		if (mexOutputFilePath != null) {
			MEXWriter mexWriter = new MEXWriter();

			try {
				mexWriter.write(benchmarkLog, mexOutputFilePath);
				logger.info("wrote MEX file to " + mexOutputFilePath + ".ttl");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * the executor service is shut down after all scenarios have been run
	 */
	private void finalizeExecutor() {
		executorService.shutdown();
		try {
			executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			throw new RuntimeException("Threads could not finish", e);
		}
	}

	/**
	 * run a single scenario (scenario = learningtask + "/" + learningproblem)
	 * @param scn scenario
	 */
	private void runScenario(String scn) {
		final String[] split = scn.split("/");
		final String task = split[0];
		final String problem = split[1];
		runScenario(new Scenario(task, problem));
	}

	/**
	 * run a single scenario
	 * @param task learning task name
	 * @param problem learning problem name/number
	 */
	private void runScenario(String task, String problem) {

	}
	/**
	 * run a single scenario
	 * @param scn scenario
	 */
	private void runScenario(Scenario scn) {
		logger.info("executing scenario " + scn.getTask() + "/" + scn.getProblem());
		Configuration baseConf = new BaseConfiguration();

		baseConf.setProperty("framework.currentSeed", seed);

		if (folds > 1) {
			CrossValidationRunner crossValidationRunner = new CrossValidationRunner(this, scn, baseConf);
			crossValidationRunner.run();
		} else {
			throw new NotImplementedException("Absolute measure not yet implemented");
		}

	}

	/**
	 * get associated knowledge base language of a system
	 * @param sys the system
	 * @return the language
	 */
	public String getSystemLanguage(String sys) {
		return systemLanguage.get(sys);
	}

	/**
	 * get information about
	 * @param system
	 * @return
	 */
	public LearningSystemInfo getSystemInfo(String system) {
		if (!systemInfos.containsKey(system)) {
			LearningSystemInfo lsi = new LearningSystemInfo(this, system);
			systemInfos.put(system, lsi);
			return lsi;
		}
		return systemInfos.get(system);
	}

	/**
	 * @return the resultset can be obtained as apache common config after the run is finished
	 */
	public Configuration getResultset() {
		return resultset;
	}

	/**
	 * delete all files in the working directory if enabled in the config file
	 */
	public void cleanTemp() {
		if (config.isDeleteWorkDir()) {
			Path tempDirectory = getTempDirectory();
			if (tempDirectory != null) {
				logger.debug("deleting working directory: " + tempDirectory);
				try {
					FileUtils.forceDeleteOnExit(tempDirectory.toFile());
				} catch (IOException e) {
					logger.debug("could not remove working directory: " + e.getMessage());
				}
			}
		}
	}

	/**
	 * @return the benchmark log
	 */
	public BenchmarkLog getBenchmarkLog() {
		return benchmarkLog;
	}
}
