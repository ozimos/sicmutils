;;
;; Copyright © 2017 Colin Smith.
;; This work is based on the Scmutils system of MIT/GNU Scheme:
;; Copyright © 2002 Massachusetts Institute of Technology
;;
;; This is free software;  you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3 of the License, or (at
;; your option) any later version.
;;
;; This software is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with this code; if not, see <http://www.gnu.org/licenses/>.
;;

(ns sicmutils.sicm.ch6-test
  (:refer-clojure :exclude [+ - * /])
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [sicmutils.env :as e
             :refer [+ - * / D simplify compose
                     up down
                     sin cos square exp]]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]
            [sicmutils.util :as u]))

(use-fixtures :each hermetic-simplify-fixture)

(deftest section-6-2
  (let [H0 (fn [alpha]
             (fn [[_ _ ptheta]]
               (/ (square ptheta) (* 2 alpha))))
        H1 (fn [beta]
             (fn [[_ theta _]]
               (* -1 beta (cos theta))))
        H-pendulum-series (fn [alpha beta epsilon]
                            (e/series (H0 alpha) (* epsilon (H1 beta))))
        W (fn [alpha beta]
            (fn [[_ theta ptheta]]
              (/ (* -1 alpha beta (sin theta)) ptheta)))
        a-state (up 't 'theta 'p_theta)
        L (e/Lie-derivative (W 'α 'β))
        H (H-pendulum-series 'α 'β 'ε)
        E (((exp (* 'ε L)) H) a-state)
        solution0 (fn [alpha beta]
                    (fn [t]
                      (fn [[t0 theta0 ptheta0]]
                        (up t
                            (+ theta0 (/ (* (- t t0) ptheta0) alpha))
                            ptheta0))))
        C (fn [alpha beta epsilon order]
            (fn [state]
              (e/series:sum
               (((e/Lie-transform (W alpha beta) epsilon)
                 identity)
                state)
               order)))
        C-inv (fn [alpha beta epsilon order]
                (C alpha beta (- epsilon) order))
        solution (fn [epsilon order]
                   (fn [alpha beta]
                     (fn [delta-t]
                       (compose (C alpha beta epsilon order)
                                ((solution0 alpha beta) delta-t)
                                (C-inv alpha beta epsilon order)))))]
    (is (e/zero?
         (simplify ((+ ((e/Lie-derivative (W 'alpha 'beta)) (H0 'alpha))
                       (H1 'beta))
                    a-state))))

    (is (= '((/ (* (/ 1 2) (expt p_theta 2)) α)
             0
             (/ (* (/ 1 2) α (expt β 2) (expt ε 2) (expt (sin theta) 2))
                (expt p_theta 2))
             0
             0)
           (e/freeze
            (simplify (take 5 E)))))

    (is (= '(/ (+ (* (/ 1 2)
                     (expt α 2)
                     (expt β 2)
                     (expt ε 2)
                     (expt (sin theta) 2))
                  (* (/ 1 2) (expt p_theta 4)))
               (* (expt p_theta 2) α))
           (e/freeze
            (simplify
             (e/series:sum E 2)))))

    (is (= '(up t
                (/ (+ (* (/ -1 2)
                         (expt α 2) (expt β 2) (expt ε 2)
                         (sin theta) (cos theta))
                      (* (expt p_theta 2) α β ε (sin theta))
                      (* (expt p_theta 4) theta))
                   (expt p_theta 4))
                (/ (+ (* (expt p_theta 2) α β ε (cos theta))
                      (* (/ -1 2) (expt α 2) (expt β 2) (expt ε 2))
                      (expt p_theta 4))
                   (expt p_theta 3)))
           (e/freeze
            (simplify
             ((C 'α 'β 'ε 2) a-state)))))))
