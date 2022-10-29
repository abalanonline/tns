/*
 * Copyright 2022 Aleksei Balan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ab;

import java.util.function.UnaryOperator;

@Deprecated
public class QuadraticRandom implements UnaryOperator<Double> {
  private final double aConstant;
  private final double bConstant;
  private final double cConstant;

  public QuadraticRandom(double x1, double y1, double x2, double y2, double x3, double y3) {
    // boring computations
    double a1 = x1 * x1 - x2 * x2;
    double a2 = x2 * x2 - x3 * x3;
    double b1 = x1 - x2;
    double b2 = x2 - x3;
    double c1 = y1 - y2;
    double c2 = y2 - y3;
    double aa1 = a1 * b2;
    double aa2 = a2 * b1;
    double cc1 = c1 * b2;
    double cc2 = c2 * b1;
    aConstant = (cc1 - cc2) / (aa1 - aa2);
    bConstant = (c1 - aConstant * a1) / b1;
    cConstant = y1 - aConstant * x1 * x1 - bConstant * x1;
  }

  @Override
  public Double apply(Double d) {
    return aConstant * d * d + bConstant * d + cConstant;
  }

  public int apply(int i) {
    return (int) Math.round(this.apply(Double.valueOf(i)));
  }
}
