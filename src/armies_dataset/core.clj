(ns armies-dataset.core
  "Code to build the wars dataset"
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [net.cgrand.enlive-html :as html])
  (:use [clojure.pprint :only [pprint]])
  (:import [java.io StringReader PushbackReader]
           [com.google.common.base CharMatcher]))

(def *src-urls*
  {:1000-1499 "http://en.wikipedia.org/wiki/List_of_wars_1000%E2%80%931499"
   :1500-1799 "http://en.wikipedia.org/wiki/List_of_wars_1500%E2%80%931799"
   :1800-1899 "http://en.wikipedia.org/wiki/List_of_wars_1800%E2%80%9399"
   :1900-1944 "http://en.wikipedia.org/wiki/List_of_wars_1900%E2%80%9344"
   :1945-1989 "http://en.wikipedia.org/wiki/List_of_wars_1945%E2%80%9389"
   :1990-2002 "http://en.wikipedia.org/wiki/List_of_wars_1990%E2%80%932002"
   :2003-2010 "http://en.wikipedia.org/wiki/List_of_wars_2003%E2%80%9310"
   :2011-present "http://en.wikipedia.org/wiki/List_of_wars_2011%E2%80%93present"})

(def *corpus-file* "wars_1000_to_now.corpus")

(def *stats-file* "wars_1000_to_now.clj")

(def *per-decade-file* "participants_by_decade.clj")

(def *top-players* "top_players.clj")

(def *active-armies-per-decade* "active_armies_per_decade.clj")

(defn download-corpus
  []
  (pprint
   (into
    {}
    (map
     (fn [[time url]]
       [time (-> url
                 client/get
                 :body)])
     *src-urls*))
   (io/writer *corpus-file*)))

(defn process-cell
  "Code to format a wikipedia cell
   and process it"
  [a-cell]
  (filter
   (fn [x]
     (not= "" x))
   (map
    (fn [x]
      (string/replace
       (nth
        (re-find #"(\[.*\])?(.*)" x)
        2)
       #"\[.*\]|\(.*\)"
       ""))
    (string/split-lines a-cell))))

(defn extract-tables
  [body]
  ;; two rests used to ignore
  ;; table heading rows
  (flatten
   (map
    (fn [a-row]
      (let [elements (map
                      (fn [[start end name winners losers]]
                        {:start (first start)
                         :end (first end)
                         :name (first name)
                         :winners (map string/trim winners)
                         :losers (map string/trim losers)})
                      (partition
                       5
                       5
                       (map
                        #(-> % html/text process-cell)
                        (html/select a-row [:td]))))]
        elements))
    (rest
     (rest
      (html/select
       (html/html-resource
        (StringReader. body))
       [:table.wikitable :tr]))))))

(defn process-corpus
  []
  (clojure.pprint/pprint
   (reduce
    concat
    []
    (map
     (fn [[_ x]]
       (extract-tables x))
     (read
      (PushbackReader.
       (io/reader *corpus-file*)))))
   (io/writer *stats-file*)))

(defn participants-histogram
  "Generate a file containing
   start-date and participants in the war"
  []
  (sort-by
   :start
   (filter
    (fn [x]
      (and (-> x :participants empty? not)
           (-> x :start nil? not)))
    (map
     (fn [{start   :start
          end     :end     
          name    :name    
          winners :winners 
          losers  :losers  }]
       {:start (try (Integer/parseInt start)
                    (catch NumberFormatException e nil))
        :participants (map
                       #(.trimFrom CharMatcher/WHITESPACE %)
                       (concat winners losers))})
     (read
      (PushbackReader.
       (io/reader *stats-file*)))))))

(defn participants-histogram-by-decade
  "A list of participants by the decade"
  []
  (let [histogram (sort-by
                   first
                   (map
                    (fn [[decade participants]]
                      [decade
                       (reverse
                        (sort-by second participants))])
                    (reduce
                     (fn [acc {start :start participants :participants}]
                       (let [decade (* 10 (quot start 10))
                             participants-freq (frequencies participants)]
                         (merge-with
                          (fn [x y]
                            (merge-with + x y))
                          acc
                          {decade participants-freq})))
                     {}
                     (participants-histogram))))]
    (pprint histogram (io/writer *per-decade-file*))))

(defn important-entities-list
  []
  (let [per-decade (read
                    (PushbackReader.
                     (io/reader *per-decade-file*)))
        participants (map
                      first
                      (sort-by
                       second
                       (filter
                        (fn [[x n]]
                          (>= n 4))
                        (reduce
                         (fn [acc freqs]
                           (merge-with + acc freqs))
                         (map
                          (fn [[decade participants]]
                            (into {} participants))
                          per-decade)))))]
    (pprint
     participants
     (io/writer *top-players*))))

(defn active-armies-by-decade
  []
  (let [top-players (set
                     (read
                      (PushbackReader.
                       (io/reader *top-players*))))

        per-decade-players (read
                            (PushbackReader.
                             (io/reader *per-decade-file*)))]
    (map
     (fn [[decade players]]
       (let [top10 (set
                    (take
                     5
                     (reverse
                      (sort-by
                       second
                       players))))
             in-top (set
                     (filter
                      (fn [[x n]]
                        (some #{x} top-players))
                      players))]
         [decade (sort-by second (clojure.set/union top10 in-top))]))
     (pprint per-decade-players (io/writer *active-armies-per-decade*)))))

(defn generate-data-for-plot
  []
  (let [data (read
              (PushbackReader.
               (io/reader *active-armies-per-decade*)))
        entities (flatten
                  (map
                   (fn [[decade players]]
                     (map
                      first
                      players))
                   data))

        rows     (map
                  (fn [[decade players]]
                    (let [players-dict (into {} players)]
                      (cons
                       decade
                       (map
                        (fn [x]
                          (or (players-dict x)
                              0))
                        entities))))
                  data)

        rows-with-header (map
                          (fn [a-row]
                            (string/join ", " a-row))
                          (apply
                           map
                           list
                           (cons (cons "Decade"
                                       entities)
                                 rows)))]

    (with-open [wrtr (io/writer "active_armies_by_decade.csv")]
      (binding [*out* wrtr]
        (doseq [row rows-with-header]
          (println row))))))

(def *wars-started-per-decade* "wars_started_per_decade.csv")

(defn wars-started-per-decade
  []
  (let [data  (sort-by
               first
               (reduce
                (fn [acc {start   :start
                         end     :end     
                         name    :name    
                         winners :winners 
                         losers  :losers}]
                  (let [start-year
                        (try (Integer/parseInt start)
                             (catch NumberFormatException e nil))

                        start-decade
                        (if start-year (* 10 (quot start-year 10)) nil)]
                    
                    (if start-year
                      (merge-with + acc {start-decade 1})
                      acc)))
                {}
                (read
                 (PushbackReader.
                  (io/reader *stats-file*)))))]
    (with-open [wrtr (io/writer *wars-started-per-decade*)]
      (binding [*out* wrtr]
        (println "Decade,Count")
        (doseq [row data]
          (println (string/join "," row)))))))

(def *armies-engaged-per-decade* "armies_engaged_per_decade.csv")

(defn armies-engaged-per-decade
  []
  (let [data (sort-by
              first
              (reduce
               (fn [acc {start   :start
                        end     :end
                        name    :name
                        winners :winners
                        losers  :losers}]
                 (let [start-year
                       (try (Integer/parseInt start)
                            (catch NumberFormatException e nil))
                      
                       start-decade
                       (if start-year (* 10 (quot start-year 10)) nil)

                       participants (count
                                     (clojure.set/union (set winners) (set losers)))]
                   (if start-year
                     (merge-with + acc {start-decade participants})
                     acc)))
               {}
               (read
                (PushbackReader.
                 (io/reader *stats-file*)))))]
    (with-open [wrtr (io/writer *armies-engaged-per-decade*)]
      (binding [*out* wrtr]
        (println "Decade,Count")
        (doseq [row data]
          (println (string/join "," row)))))))

(defn armies-engaged-per-decade
  []
  (let [data (sort-by
              first
              (reduce
               (fn [acc {start   :start
                        end     :end
                        name    :name
                        winners :winners
                        losers  :losers}]
                 (let [start-year
                       (try (Integer/parseInt start)
                            (catch NumberFormatException e nil))
                      
                       start-decade
                       (if start-year (* 10 (quot start-year 10)) nil)

                       participants (count
                                     (clojure.set/union (set winners) (set losers)))]
                   (if start-year
                     (merge-with + acc {start-decade participants})
                     acc)))
               {}
               (read
                (PushbackReader.
                 (io/reader *stats-file*)))))]
    (with-open [wrtr (io/writer *armies-engaged-per-decade*)]
      (binding [*out* wrtr]
        (println "Decade,Count")
        (doseq [row data]
          (println (string/join "," row)))))))

(defn most-active-armies-per-century
  []
  (let [data
        (reduce
         (fn [acc {start   :start
                  end     :end
                  name    :name
                  winners :winners
                  losers  :losers}]
           (let [start-year
                 (try (Integer/parseInt start)
                      (catch NumberFormatException e nil))
                 
                 start-decade
                 (if start-year (* 100 (quot start-year 100)) nil)

                 winners-table (reduce
                                (fn [acc w]
                                  (merge-with + acc {w 1}))
                                {}
                                (distinct (map
                                           #(.trimFrom CharMatcher/WHITESPACE %)
                                           winners)))

                 losers-table (reduce
                               (fn [acc l]
                                 (merge-with + acc {l 1}))
                               {}
                               (distinct (map
                                          #(.trimFrom CharMatcher/WHITESPACE %)
                                          losers)))
                 
                 participants-table (merge-with + winners-table losers-table)]
             (if start-year
               (merge-with
                (fn [x y]
                  (merge-with + x y))
                acc
                {start-decade participants-table})
               acc)))
         {}
         (read
          (PushbackReader.
           (io/reader *stats-file*))))

        per-century-most-active (sort-by
                                 first
                                 (map
                                  (fn [[century tally]]
                                    [century (take 5 (reverse (sort-by second tally)))])
                                  data))]
    (with-open [wrtr (io/writer "per_century_most_active.clj")]
      (binding [*out* wrtr]
       (do
         (println "<div class='per-century-armies'>")
         (doseq [[century entities] per-century-most-active]
           (println "<strong>" century "A.D." "</strong>")
           (println "<table border='1' style='margin-left:auto; margin-right:auto'><tr><th>Army</th><th>Number of Wars</th></tr>")
           (doseq [[army count] entities]
             (println (str "<tr><td>" army "</td><td>" count "</td></tr>")))
           (println "</table>"
                    "</td>"))
         (println "</div>"))))))

(defn most-active-armies-millennium
  []
  (take
   10
   (reverse
    (sort-by
     second
     (reduce
      (fn [acc {start   :start
               end     :end
               name    :name
               winners :winners
               losers  :losers}]
        (let [participants (reduce
                            (fn [acc p]
                              (merge-with + acc {p 1}))
                            {}
                            (map
                             #(.trimFrom CharMatcher/WHITESPACE %)
                             (concat winners losers)))]
          (merge-with + acc participants)))
      {}
      (read
       (PushbackReader.
        (io/reader *stats-file*))))))))
