;
; Copyright © 2017 Colin Smith.
; This work is based on the Scmutils system of MIT/GNU Scheme:
; Copyright © 2002 Massachusetts Institute of Technology
;
; This is free software;  you can redistribute it and/or modify
; it under the terms of the GNU General Public License as published by
; the Free Software Foundation; either version 3 of the License, or (at
; your option) any later version.
;
; This software is distributed in the hope that it will be useful, but
; WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
; General Public License for more details.
;
; You should have received a copy of the GNU General Public License
; along with this code; if not, see <http://www.gnu.org/licenses/>.
;

(ns sicmutils.polynomial-gcd-test
  (:import (com.google.common.base Stopwatch)
           (java.util.concurrent TimeUnit))
  (:require [clojure.test :refer :all]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [sicmutils
             [polynomial :refer :all]
             [polynomial-gcd :refer :all]
             [polynomial-test :as p-test]
             [generic :as g]
             [analyze :as a]
             [expression :refer [variables-in]]]
            [clojure.tools.logging :as log]
            [clojure.test.check.generators :as gen]))

(deftest poly-gcd
  (let [X (make 2 [[[1 0] 1]]) ;; some polynomials of arity 2
        Y (make 2 [[[0 1] 1]])]
    (testing "inexact coefficients"
      (is (= (make [1]) (gcd (make [0.2 0.4 0.6]) (make [0.4 0.6 0.8])))))
    (testing "GCD: arity 1 case"
      (let [x+1 (make [1 1])
            x+2 (make [2 1])
            x+3 (make [3 1])
            x+4 (make [4 1])
            U (mul x+1 (mul x+1 (mul x+2 x+4)))
            V (mul x+1 (mul x+2 x+3))]
        (is (= (make [2 3 1]) (gcd U V)))
        (is (= (make [4]) (gcd (make [8]) (make [4]))))
        (is (= (make [1]) (gcd (make [7]) (make [11]))))
        (is (= (make [11]) (gcd (make []) (make [11])))))
      (let [x+4 (make [4 1])
            x+3 (make [3 1])
            x-2 (make [-2 1])
            x+1 (make [1 1])
            U (reduce mul [x+4 x+4 x+3 x+3 x-2 x-2 x-2 x-2 x-2 x+1])
            V (reduce mul [x+4 x+3 x+3 x+3 x-2 x-2 x+1 x+1])
            W (reduce mul [x+4 x+3 x+3 x-2 x-2 x+1])
            Z (make [])]
        (is (= W (gcd U V)))
        (is (= W (gcd V U)))
        (is (= U (gcd U U)))
        (is (= V (gcd V V)))
        (is (= W (gcd W W)))
        (is (= U (gcd U Z)))
        (is (= U (gcd Z U)))
        (is (= V (gcd V Z)))
        (is (= V (gcd Z V)))))
    (testing "divide constant arity 2"
      (is (= [(make 2 []) X] (divide X Y)))
      (is (= [(make 2 []) Y] (divide Y X))))
    (testing "GCD: arity 2 case"
      (let [I (make 2 [[[0 0] 1]])
            X (make 2 [[[1 0] 1]])
            Y (make 2 [[[0 1] 1]])
            X+Y (add X Y)
            X+1 (add X I)
            Y+1 (add Y I)
            X+Y_2 (mul X+Y X+Y)
            X+Y_3 (mul X+Y_2 X+Y)
            U (reduce mul [(expt X+1 3) (expt X+Y 2) (expt Y+1 4)])
            V (reduce mul [(expt X+1 2) (expt X+Y 5) (expt Y+1 3)])
            G (reduce mul [(expt X+1 2) (expt X+Y 2) (expt Y+1 3)])]
        (is (= X+Y_2 (gcd X+Y_2 X+Y_3)))
        (is (= X+Y_3 (gcd X+Y_3 X+Y_3)))
        (is (= G (gcd U V)))))
    (testing "GCD: arity 3 case"
      (binding [*poly-gcd-time-limit* [2 TimeUnit/SECONDS]]
        (let [I (make 3 [[[0 0 0] 1]])
              X (make 3 [[[1 0 0] 1]])
              Y (make 3 [[[0 1 0] 1]])
              Z (make 3 [[[0 0 1] 1]])
              X+Y (add X Y)
              X+Z (add X Z)
              Y+Z (add Y Z)
              X+Y+Z (add X+Y Z)
              X+1 (add X I)
              Y+1 (add Y I)
              Z+1 (add Z I)
              U (reduce mul [(expt X+1 3) (expt X+Y 2) (expt Y+Z 5) (expt X+Y+Z 4) (expt Y+1 4) (expt Z+1 3)])
              V (reduce mul [(expt X+1 2)  (expt X+Y 5) (expt Y+Z 3) (expt X+Y+Z 5) (expt Y+1 2) (expt Z+1 1) X+1])
              G (reduce mul [(expt X+1 3) (expt X+Y 2) (expt Y+Z 3) (expt X+Y+Z 4) (expt Y+1 2) Z+1])]
          (is (= [(reduce mul [(expt Y+Z 2) (expt Y+1 2) (expt Z+1 2)]) (make 3 [])] (divide U G)))
          (is (= [(reduce mul [(expt X+Y 3) X+Y+Z]) (make 3 [])] (divide V G)))
          (is (= X+Z (gcd (mul X+Y X+Z) (mul Y+Z X+Z))))
          (is (= (mul X+Z X+Y+Z) (gcd (reduce mul [X+Y X+Z X+Y+Z]) (reduce mul [X+Z X+Y+Z Y+Z]))))
          (is (= (mul X+Z (mul X+Z X+Y)) (gcd (reduce mul [X+Z X+Z X+Y X+Y+Z Y+1]) (reduce mul [X+Z X+Z X+Y X+1 Z+1 X+Z]))))
          (is (= G (gcd U V))))))
    ;; this was sort of cool, but prevented us from using native BigInteger GCD.
    ;; it could come back, but in order to do this right, we would need a way
    ;; to specify the coefficient field when we create a polynomial so that we
    ;; can efficiently dispatch to a GCD routine tailored to that field.
    #_(testing "modular polynomial reduction"
        (let [A (make [-360 -171 145 25 1])
              B (make [-15 -14 -1 15 14 1])
              Z5 #(modular/make % 5)
              A:Z5 (map-coefficients Z5 A)
              B:Z5 (map-coefficients Z5 B)
              G5 (gcd A:Z5 B:Z5)]
          (is (= (make [(Z5 0) (Z5 -1) (Z5 0) (Z5 0) (Z5 1)]) A:Z5))
          (is (= (make [(Z5 0) (Z5 1) (Z5 -1) (Z5 0) (Z5 -1) (Z5 1)]) B:Z5))
          (is (= (make [(Z5 0) (Z5 -1) (Z5 0) (Z5 0) (Z5 1)]) G5))))))

(deftest basics
  (let [U (make [-5 2 8 -3 -3 0 1 0 1])
        V (make [21 -9 -4 0 5 0 3])]
    (is (= (make [-9 0 3 0 -15]) (univariate-pseudo-remainder U V)))
    (is (= (map make [[1 2 2]
                      [3 0 4]
                      [2 0 4 0 1]
                      [7 0 5 0 8]
                      [4 0 3 0 11]
                      [11 0 2 0 7]
                      [6 0 17 0 10]])
           (map #(univariate-modular-remainder % U V) [3 5 7 11 13 17 19])))
    (is (= (g/one-like U) (univariate-subresultant-gcd U V)))))

(deftest poly-modular-gcd
  ;; The test data here is courtesy of the following Mathematica expressions:
  ;;
  ;; randomPoly[] := Expand[Times @@ ((x - #) & /@ Table[RandomInteger[{-10, 10}],
  ;;                                                     RandomInteger[{1, 10}]])]
  ;;
  ;; SeedRandom[314159]; Table[{CoefficientList[uv[[1]], x],
  ;;   CoefficientList[uv[[2]], x],
  ;;   PolynomialGCD[uv[[1]], uv[[2]], Modulus -> 7],
  ;;   PolynomialGCD[uv[[1]], uv[[2]], Modulus -> 5]},
  ;;  {uv, Table[{randomPoly[], randomPoly[]}, 20]}] // MatrixForm
  ;;
  (let [gcd-mod (fn [m] (partial univariate-modular-gcd m))
        us (map make [[0, -38880, -32292, 14028, 7259, -350, -168, 2, 1]
                      [-84672, 70560, -15324, -1636, 887, -45, -11, 1]
                      [-28800, -6800, 21488, -2032, -1612, -79, 14, 1]
                      [-26880, -17056, -2336, 650, 235, 26, 1]
                      [0, 36, 36, -31, -40, -6, 4, 1]
                      [194400, 102600, -6444, -11526, -1855, 5, 19, 1]
                      [0, -10800000, 3270000, 400000, -196000, 5180, 2933, -181, -13, 1]
                      [-810, -1467, -711, 10, 80, 17, 1]
                      [0, -33600, 58000, -26864, 1520, 1072, -121, -8, 1]
                      [0, -10800, 1980, 1776, -283, -73, 7, 1]
                      [43200, -16440, -54956, 18762, 12895, -2291, -1154, -32, 15, 1]
                      [-80640, -122208, -34376, 11308, 3998, -233, -115, 1, 1]
                      [0, 0, 0, -900, -360, -11, 10, 1]
                      [0, 96, 68, 15, 1]
                      [-13440, 21296, -9236, 1400, -5, -16, 1]
                      [7, 8, 1]
                      [8, 1]
                      [-96, 76, -16, 1]
                      [0, -5292, -3087, 5547, 3493, -209, -413, -47, 7, 1]
                      [-960, 944, -176, -48, 8, 1]])
        vs (map make [[120, 74, 15, 1]
                      [-69120, 20352, 73976, -21822, -4851, 1488, -6, -18, 1]
                      [21600, -32520, 9332, 2350, -703, -71, 11, 1]
                      [-5, 1]
                      [360, 347, -25, -11, 1]
                      [12000, 3400, -420, -134, 3, 1]
                      [4 1]
                      [0, -224, 304, -50, -35, 4, 1]
                      [-11200, -2600, 1412, -74, -13, 1]
                      [490, 189, 24, 1]
                      [268800, 228800, 44112, -5088, -1968, -72, 15, 1]
                      [-7200, -2960, 66, 169, 24, 1]
                      [9450, -1905, -9922, 1995, 478, -91, -6, 1]
                      [13335840, -5990544, -3657024, 1868232, 67834, -100273, 3713, 1958, -124, -13, 1]
                      [0, 1]
                      [-7348320, 12258864, -5223690, -90675, 483489, -74459, -7427, 2287, -53, -17, 1]
                      [-2880, 4496, -1949, 363, -31, 1]
                      [0, 10500, 4300, -1595, -572, 22, 16, 1]
                      [-17010, -2619, 1212, 2, -18, 1]
                      [-600, 20, 114, -21, 1]])]
    (is (= (map make [[1 4 1 1]
                      [3 6 4 1]
                      [3 6 0 5 2 1]
                      [1]
                      [6 0 1]
                      [6 5 4 1]
                      [4 1]
                      [4 5 5 1]
                      [0, 2, 5, 6, 1]
                      [0, 3, 1]
                      [6, 4, 6, 1]
                      [2, 3, 3, 1]
                      [0, 2, 6, 5, 1]
                      [0, 4, 5, 1]
                      [0, 1]
                      [0, 1, 1]
                      [1]
                      [5, 1]
                      [0, 5, 0, 1]
                      [1]])
           (map (gcd-mod 7)  us vs)))
    (is (= (map make [[0 4 0 1]
                      [3 2 4 0 2 3 1]
                      [0 0 2 4 3 1]
                      [0 1]
                      [0 2 3 1]
                      [0 0 1 3 1]
                      [4 1]
                      [0 4 0 0 0 1]
                      [0, 0, 3, 1]
                      [0, 2, 1]
                      [0, 0, 1, 3, 2, 3, 1]
                      [0, 1, 1]
                      [0, 0, 4, 0, 1]
                      [0, 2, 2, 1]
                      [0, 1]
                      [2, 3, 1]
                      [1]
                      [2, 1]
                      [0, 1, 1, 1, 1]
                      [0, 1]])
         (map (gcd-mod 5) us vs)))
    (is (= (map make [[1]
                      [-3 1]
                      [-180 52 17 1]
                      [1]
                      [1 1]
                      [-400 -100 4 1]
                      [1]
                      [1]
                      [700, -100, -7, 1]
                      [10, 1]
                      [10, 1]
                      [-20, 1, 1]
                      [6, 1]
                      [1]
                      [1]
                      [7, 1]
                      [1]
                      [1]
                      [21, 10, 1]
                      [1]])
           (map gcd us vs)))))

(deftest simple-gcd-3
  (testing "GCD: arity 3 case"
    (let [I (make 3 [[[0 0 0] 1]])
          II (add I I)
          X (make 3 [[[1 0 0] 1]])
          Y (make 3 [[[0 1 0] 1]])
          Z (make 3 [[[0 0 1] 1]])
          X+Y (add X Y)
          X+Z (add X Z)
          Y+Z (add Y Z)]
      (is (= X+Z (gcd (mul X+Y X+Z) (mul Y+Z X+Z))))
      (is (= II (gcd II (add Z Z))))
      (is (= II (gcd (add Z Z) II)))
      (is (= II (gcd II (add (add X X) (add Z Z))))))))

(def ^:private poly-analyzer (->PolynomialAnalyzer))
(defn ^:private ->poly [x] (a/expression-> poly-analyzer x (fn [p _] p)))

(defn ^:private gcd-test [name dx fx gx]
  (let [d (->poly dx)
        f (->poly fx)
        g (->poly gx)
        df (mul d f)
        dg (mul d g)
        sw (Stopwatch/createStarted)
        a (binding [*poly-gcd-time-limit* [10 TimeUnit/SECONDS]
                    *poly-gcd-cache-enable* false]
            (is (= d (gcd df dg)))            )]
    (log/info "gcd-test" name (str sw))))

(deftest ^:long ^:benchmark gjs
  (testing "GJS cases (see sparse-gcd.scm:666)"
    (let [d1 '(+ (expt x1 2) x1 3)
          f1 '(+ (* 2 (expt x1 2)) (* 2 x1) 1)
          g1 '(+ (expt x1 2) (* 2 x1) 2)

          d2 '(+ (* 2 (expt x1 2) (expt x2 2))
                 (* x1 x2)
                 (* 2 x1))
          f2 '(+ (expt x2 2)
                 (* 2 (expt x1 2) x2)
                 (expt x1 2)
                 1)
          g2 '(+ (* (expt x1 2) (expt x2 2))
                 (* (expt x1 2) x2)
                 (* x1 x2)
                 (expt x1 2)
                 x1)

          d3 '(+ (* x2 x2 x3 x3)
                 (* x2 x2 x3)
                 (* 2 x1 x1 x2 x3)
                 (* x1 x3))
          f3 '(+ (* x3 x3)
                 (* x2 x2 x3)
                 (* x1 x1 x2 x3)
                 (* x1 x3)
                 (* x1 x1 x2 x2))
          g3 '(+ (* x2 x3)
                 (* 2 x1 x3)
                 x3
                 x1)

          d4 '(+ (* x1 x1 x4 x4)
                 (* x2 x2 x3 x4)
                 (* x1 x1 x2 x4)
                 (* x2 x4)
                 (* x1 x1 x2 x3))
          f4 '(+ (* x1 x2 x3 x3 x4 x4)
                 (* x1 x3 x3 x4 x4)
                 (* x1 x4 x4)
                 (* x4 x4)
                 (* x1 x3 x4))
          g4 '(+ (* x1 x3 x3 x4 x4)
                 (* x3 x3 x4 x4)
                 (* x4 x4)
                 (* x1 x2 x2 x3 x4)
                 (* x1 x2 x2))

          d5 '(+ (* x1 x1 x1 x2 x2 x3 x3 x4 x5 x5)
                 (* x1 x2 x2 x5 x5)
                 (* x1 x1 x1 x3 x4 x4 x5)
                 (* x1 x1 x1 x2 x3 x3 x4 x5)
                 (* x1 x1 x2 x3 x3 x4 x4))
          f5 '(+ (* x1 x2 x2 x5 x5)
                 (* x1 x2 x3 x3 x4 x5)
                 (* x1 x2 x3 x3 x4 x4)
                 (* x1 x2 x2 x4 x4)
                 1)
          g5 '(+ (* x1 x3 x3 x4 x5 x5)
                 (* x2 x5 x5)
                 (* x1 x2 x4 x5)
                 (* x2 x5)
                 (* x1 x2 x3 x4 x4))
          d6 '(+ (* x1 x2 x4 x4 x5 x5 x6 x6)
                 (* x1 x2 x2 x3 x3 x4 x5 x5 x6 x6)
                 (* x1 x1 x3 x6 x6)
                 (* x1 x1 x2 x3 x3 x4 x5 x5 x6)
                 (* x1 x1 x3 x5 x6))
          f6 '(+ (* x1 x1 x2 x4 x5 x5 x6 x6)
                 (* x1 x3 x5 x5 x6 x6)
                 (* x1 x2 x2 x6 x6)
                 (* x1 x1 x2 x2 x3 x3 x5 x6)
                 (* x1 x3 x3 x4 x5))
          g6 '(+ (* x2 x2 x3 x3 x4 x5 x5 x6)
                 (* x1 x4 x4 x5 x6)
                 (* x2 x2 x3 x3 x4 x5 x6)
                 (* x1 x2 x2 x3 x4 x4 x6)
                 (* x1 x1 x3 x5 x5))
          d7 '(+ (* x1 x2 x2 x4 x4 x6 x6 x7 x7)
                 (* x1 x1 x3 x4 x6 x6 x7 x7)
                 (* x3 x3 x4 x4 x7 x7)
                 (* x1 x1 x2 x4 x4 x6)
                 (* x3 x4 x5 x5))
          f7 '(+ (* x1 x1 x2 x4 x4 x5 x6 x6 x7 x7)
                 (* x1 x2 x3 x6 x7)
                 (* x3 x4 x4 x5 x5 x7)
                 (* x1 x1 x2 x3 x4 x4 x5 x6))
          g7 '(+ (* x1 x3 x5 x6 x6 x7 x7)
                 (* x2 x2 x3 x3 x4 x4 x5 x6 x7 x7)
                 (* x4 x6 x7 x7)
                 (* x1 x1 x2 x3 x5 x6 x7)
                 (* x1 x1 x3 x3 x4 x5 x5))]

      (is (= (->poly d2) (-> d2 ->poly lower-arity raise-arity)))
      (is (= (->poly d3) (-> d3 ->poly lower-arity raise-arity)))
      (is (= (->poly d4) (-> d4 ->poly lower-arity raise-arity)))

      (is (= (make [0
                    (make [2 1])
                    (make [0 0 2])])
             (-> d2 ->poly lower-arity)))

      (is (= (make [0
                    (make [2 1 2 1])
                    (make [0 0 2 0 2])
                    (make [2 5 2])
                    (make [0 0 2 4])])
             (lower-arity (mul (->poly d2) (->poly f2)))))

      (is (= (make [0
                    0
                    (make [4 4 5 4 1])
                    (make [0 0 8 4 8 4])
                    (make [4 12 9 2 4 0 4])
                    (make [0 0 8 20 8])
                    (make [0 0 0 0 4 8])])
             (mul (lower-arity (->poly d2))
                  (lower-arity (mul (->poly d2) (->poly f2))))))

      (gcd-test "D1" d1 f1 g1)
      (gcd-test "D2" d2 f2 g2)
      (gcd-test "D3" d3 f3 g3)
      (gcd-test "D4" d4 f4 g4)
      (gcd-test "D5" d5 f5 g5)
      ;; the following are too big for our naive algorithm.
      ;;(gcd-test "D6" d6 f6 g6)
      #_(gcd-test "D7" d7 f7 g7)
      (gcd-stats))))

(deftest big-gcd
  (let [u (make 10 [[[0 0 1 0 0 0 1 1 0 1] 1]
                    [[0 1 1 0 0 0 1 1 1 0] 2]
                    [[0 0 1 2 0 1 0 1 0 1] 1]
                    [[0 0 1 2 1 0 0 1 0 1] -1]
                    [[1 1 0 1 0 1 0 2 0 0] 1]
                    [[1 1 0 1 1 0 0 2 0 0] -1]
                    [[2 0 1 0 0 1 0 1 0 1] -1]
                    [[2 0 1 0 1 0 0 1 0 1] 1]
                    [[0 1 1 2 0 1 0 1 1 0] -2]
                    [[1 0 2 1 0 1 0 0 1 1] 2]
                    [[1 0 2 1 1 0 0 0 1 1] -2]])
        v (make 10 [[[0 0 1 4 1 1 0 0 0 0] 1]
                    [[2 0 1 2 1 1 0 0 0 0] 2]
                    [[4 0 1 0 1 1 0 0 0 0] 1]])]
    (let [t (fn []
              (let [g (gcd u v)]
                (gcd-stats)
                g))]
      (is (= (make 10 [[[0 0 0 0 0 0 0 0 0 0] 1]]) (t)))
      ;; for profiling
      (binding [*poly-gcd-cache-enable* false]
        (dotimes [_ 0] (t))))))

(deftest ^:long troublesome-gcd
  (let [u (make 10 [[[0 1 1 2 1 0 1 1 0 1] -1]
                    [[2 1 1 0 0 1 1 1 0 1] -1]
                    [[0 2 1 2 1 0 1 1 1 0] -2]
                    [[1 1 2 1 0 1 1 0 1 1] 1]
                    [[1 1 2 1 1 0 1 0 1 1] -1]
                    [[2 2 1 0 0 1 1 1 1 0] -2]
                    [[0 1 1 4 1 1 0 1 0 1] -1]
                    [[0 1 1 4 2 0 0 1 0 1] 1]
                    [[1 2 0 3 1 1 0 2 0 0] -1]
                    [[1 2 0 3 2 0 0 2 0 0] 1]
                    [[1 2 2 1 0 1 1 0 2 0] 1]
                    [[1 2 2 1 1 0 1 0 2 0] -1]
                    [[2 1 1 2 0 2 0 1 0 1] 1]
                    [[2 1 1 2 1 1 0 1 0 1] -2]
                    [[2 1 1 2 2 0 0 1 0 1] 1]
                    [[3 2 0 1 0 2 0 2 0 0] -1]
                    [[3 2 0 1 1 1 0 2 0 0] 1]
                    [[4 1 1 0 0 2 0 1 0 1] 1]
                    [[4 1 1 0 1 1 0 1 0 1] -1]
                    [[0 2 1 4 1 1 0 1 1 0] 2]
                    [[1 1 2 3 0 2 0 0 1 1] -1]
                    [[1 1 2 3 2 0 0 0 1 1] 1]
                    [[2 2 1 2 0 2 0 1 1 0] 2]
                    [[2 2 1 2 2 0 0 1 1 0] 2]
                    [[3 1 2 1 0 2 0 0 1 1] -1]
                    [[3 1 2 1 2 0 0 0 1 1] 1]
                    [[4 2 1 0 1 1 0 1 1 0] 2]
                    [[1 2 2 3 0 2 0 0 2 0] -1]
                    [[1 2 2 3 1 1 0 0 2 0] 1]
                    [[3 2 2 1 1 1 0 0 2 0] -1]
                    [[3 2 2 1 2 0 0 0 2 0] 1]])
        v (make 10 [[[0 0 1 4 1 1 0 0 0 0] 1]
                    [[2 0 1 2 1 1 0 0 0 0] 2]
                    [[4 0 1 0 1 1 0 0 0 0] 1]])]
    (is (= (make-constant 10 1) (gcd u v)))))

(deftest ^:benchmark kuniaki-tsuji-examples
  ;; (only have a few of these, will add more)
  ;; http://www.sciencedirect.com/science/article/pii/S0747717108001016
  (testing "ex1"
    (let [d '(+ (* x x) (* 2 (expt y 23) (expt z 24)))
          p '(+ (* x x) (* (expt y 23) (expt z 22)))
          q '(+ (* y z (expt x 3))
                (* 2 (expt z 35) (expt y 41) (expt x 2))
                (* (expt z 3) (expt y 5) x)
                525)]
      (gcd-test "K1" d p q)))
  (testing "ex2"
    ;; a hack, using (expt z 0) to make the arities equal
    (let [d '(+ (expt x 3) (* 2 (expt z 0) (+ (expt y 34) (expt y 75)) (expt x 2)) (expt y 84))
          p '(+ (expt x 2) (* (expt y 53) (expt z 62)))
          q '(+ (* y (expt x 8))
                (* 2 (expt y 47) (expt x 6))
                (* (+ (expt y 20) (expt y 14)) (expt x 4))
                (* (expt y 69) (expt x 3))
                (* (expt y 55) (expt x 2))
                (expt y 99)
                (* (expt y 45) x)
                54
                (expt z 0))]
      (gcd-test "K2" d p q))))

(deftest ^:benchmark some-interesting-small-examples
  "Clojure.test.check's awesome problem-shrinking feature found some
  small examples of polynomials whose GCD is difficult to compute with
  this code (at the time of this writing). Recording them here as they
  should provide excellent examples for performance experiments."
  (testing "ex1"
    (let [u (make 3 {[0 0 0] -1, [0 0 3] 1})
          v (make 3 {[0 0 0] 1, [2 3 0] 2, [0 8 1] 1, [7 0 5] -1})
          sw (Stopwatch/createStarted)]
      (gcd-test "S1" (make 3 {[0 0 0] 1}) u v)))
  (testing "ex2"
    (let [u (make 2 {[0 0] -1, [0 7] -1})
          v (make 2 {[0 0] 1, [0 1] -1, [0 4] 1, [3 3] -11, [1 9] 8, [8 5] -9, [12 1] 1})]
      (gcd-test "S2" (make 2 {[0 0] 1}) u v))))


;; Currently we only do GCD testing of univariate polynomials, because
;; we find that unfortunately clojure.test.check is very good at finding
;; polynomials even of arity 2 that will exceed the time allotment for
;; findinig GCDs. Hopefully we can fix that, but for the  present this
;; explains why we draw arities from the singleton set [1].

(def ^:private num-tests 20)

(defspec ^:long g-divides-u-and-v num-tests
  (gen/let [arity (gen/elements [1])]
    (prop/for-all [u (p-test/generate-poly arity)
                   v (p-test/generate-poly arity)]
                  (let [g (gcd u v)]
                    (or (and (g/zero? u)
                             (g/zero? v)
                             (g/zero? g))
                        (and (evenly-divide u g)
                             (evenly-divide v g)))))))

(defspec ^:long d-divides-gcd-ud-vd num-tests
  (gen/let [arity (gen/elements [1])]
    (prop/for-all [u (p-test/generate-nonzero-poly arity)
                   v (p-test/generate-nonzero-poly arity)
                   d (p-test/generate-nonzero-poly arity)]
                  (let [ud (mul u d)
                        vd (mul v d)
                        g (gcd ud vd)]
                    (and
                     (evenly-divide ud g)
                     (evenly-divide vd g)
                     (evenly-divide g d))))))
