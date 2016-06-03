/*
 * Copyright 2016 AKSW.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aksw.mlbenchmark.validation.measures;


/**
 * Custom class that represent a point in a Cartesian XY plane
 *
 * @author Giuseppe Cota <giuseppe.cota@unife.it>
 */
public class Point {

    // z-axis
    private double x;
    // y-axis
    private double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * @return the x
     */
    public double getX() {
        return x;
    }

    /**
     * @return the y
     */
    public double getY() {
        return y;
    }

    @Override
    public boolean equals(Object o) {
        return ((Point) o).x==this.x && ((Point)o).y == this.y;
    }

}