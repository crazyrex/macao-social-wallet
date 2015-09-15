;; Freecoin - digital social currency toolkit

;; part of Decentralized Citizen Engagement Technologies (D-CENT)
;; R&D funded by the European Commission (FP7/CAPS 610349)

;; Copyright (C) 2015 Dyne.org foundation
;; Copyright (C) 2015 Thoughtworks, Inc.

;; Sourcecode designed, written and maintained by
;; Denis Roio <jaromil@dyne.org>

;; With contributions by
;; Gareth Rogers <grogers@thoughtworks.com>
;; Duncan Mortimer <dmortime@thoughtworks.com>
;; Andrei Biasprozvanny <abiaspro@thoughtworks.com>

;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU Affero General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.

;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License
;; along with this program.  If not, see <http://www.gnu.org/licenses/>.
(ns freecoin.integration.api.wallets
  (:require [midje.sweet :refer :all]
            [peridot.core :as p]
            [cheshire.core :as json]
            [freecoin.integration.storage-helpers :as sh]
            [freecoin.integration.integration-helpers :as ih]
            [freecoin.core :as core]
            [freecoin.storage :as storage]
            [freecoin.auth :as auth]))

(def wallet1
  {:sso-id "user1-sso-id" :name "user1" :email "valid1@email.com"})

(def wallet2
  {:sso-id "user2-sso-id" :name "user2" :email "valid2@email.com"})

(against-background
 [(before :facts (ih/initialise-test-session ih/app-state ih/test-app-params))
  (after :facts (ih/destroy-test-sessions ih/app-state))]

 (facts "GET /participants"
        (fact "Responds with 401 when user is not authenticated"
              (let [{response :response} (-> (get-in ih/app-state [:sessions :default])
                                             (p/request "/participants"))]
                (:status response) => 401))
                ;; (:body response) => (contains #"Sorry, you are not signed in")))

        (fact "Renders the find wallet form when user is authorised"
              (let [{response :response} (-> ih/app-state
                                             (ih/create-and-sign-in :default wallet1)
                                             (get-in [:sessions :default])
                                             (p/request (str "/participants")))]
                (:status response) => 200
                (:body response) => (contains #"Search for a wallet"))))

 (facts "GET /participants"
        (fact "Responds with 401 when user is not authenticated"
              (let [{response :response} (-> (get-in ih/app-state [:sessions :default])
                                             (p/request "/participants?field=name&value=user"))]
                (:status response) => 401))
                ;; (:body response) => (contains #"Sorry, you are not signed in")))

        (facts "when user is authenticated"
               (facts "without query string"
                      (fact "retrieves all existing wallets"
                            (let [_ (doseq [n (range 10)]
                                      (->> {:name (str "name-" n)
                                            :email (str "email-" n "@test.com")}
                                           (storage/insert (:db-connection ih/app-state) "wallets")))
                                  {response :response} (-> ih/app-state
                                                           (ih/create-and-sign-in :default wallet1)
                                                           (get-in [:sessions :default])
                                                           (p/request "/participants/all"))]
                              (:status response) => 200
                              (:body response) => (contains #"name-0")
                              (:body response) => (contains #"name-9")
                              (:body response) =not=> (contains #"name-10"))))

               (facts "with query string ?field=<:field>&value=<:value>"
                      (tabular
                       (fact "Retrieves a wallet by name or by email address"
                             (let [_ (storage/insert (:db-connection ih/app-state) "wallets" wallet1)
                                   _ (storage/insert (:db-connection ih/app-state) "wallets" wallet2)
                                   {response :response} (-> ih/app-state
                                                            (ih/create-and-sign-in :default wallet1)
                                                            (get-in [:sessions :default])
                                                            (p/request (str "/participants/find?field=" ?field "&value=" ?value)))]
                               (:status response) => 200
                               (:body response) => (contains (re-pattern (str "name.+" (:name ?found))))
                               (:body response) =not=> (contains (re-pattern (str "name.+" (:name ?not-found))))))

                       ?field   ?value   ?found    ?not-found
                       "name"   "user1"  wallet1    wallet2
                       "name"   "user2"  wallet2    wallet1)

                      (fact "Responds with a 200 and reports no result when no wallets found"
                            (let [{response :response} (-> ih/app-state
                                                           (ih/create-and-sign-in :default wallet1)
                                                           (get-in [:sessions :default])
                                                           (p/request (str "/participants/find?field=name&value=user")))]
                              (:status response) => 200
                              (:body response) => (contains #"No participant found")))))))
