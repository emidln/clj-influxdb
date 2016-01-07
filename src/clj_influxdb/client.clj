(ns clj-influxdb.client
  (:require [clojure.string :as str]
            [clj-http.client :as http])
  (:import java.time.Instant))

(defn ^Long now
  "Current time in nanoseconds"
  []
  (let [i (Instant/now)]
    (-> (.getEpochSecond i)
        (* 1000000000)
        (+ (.getNano i)))))

(defn escape-key
  [s]
  (-> (name s)
      (str/escape {\, "\\,"
                   \= "\\="
                   \space "\\="})))

(defn encode-value
  "Encodes a value for InfluxDB"
  [x]
  (cond
    (integer? x) (str x "i")
    (rational? x) (encode-value (float x))
    (float? x) (str x)
    (keyword? x) (encode-value (name x))
    (string? x) (str \" (str/escape x {\" "\""}) \")
    (boolean? x) (if x "t" "f")
    :else (encode-value (str x))))

(defn encode-tag
  [x]
  (cond
    (number? x) (str x)
    (keyword? x) (name x)
    :else (str x)))

(def encode-keyish (comp escape-key encode-tag))

(defn encode-measurement-line
  "Encodes a measurement map into the InfluxDB 0.9.x line protocol"
  [{:keys [measurement tags fields timestamp]}]
  (str (encode-keyish measurement)
       (when (seq tags) ",")
       ;; tags should be escaped and sorted by key
       (some->> tags
                (map #(str/join "=" (map encode-keyish %)))
                sort
                (str/join ","))
       " "
       ;; fields are k=v pairs, field keys are the same as tag keys
       (->> fields
            (map #(str (encode-keyish (nth % 0))
                       "="
                       (encode-value (nth % 1))))
            (str/join ","))
       " "
       (or timestamp (now))))

(defn new-measurement
  "Helper to create new measurement maps"
  ([measurement tags fields]
   {:measurement measurement
    :tags tags
    :fields fields})
  ([measurement tags fields timestamp]
   (-> (new-measurement measurement tags fields)
       (assoc :timestamp timestamp))))

(def merge-keep-left
  (partial merge-with (fn [x y] x)))

(defn send-measurements!
  " Send some measurements (sequence of measurement maps)

  :db           Name of database to write measurements into (required)

  :host         Hostname to connect to (default: \"localhost\")
  :port         Port to connect to (default: \"8086\")
  :path-prefix  Path prefix to use (if InfluxDB is behind a proxy) (default: \"/\")
  :ssl?         Use HTTPS for the connection (default: false)
  :insecure?    Allow invalid SSL certificates (default: false)
  :username     Username to connnect as (optional)
  :password     Password to use when connecting with username (optional)
  :timeout      HTTP timeout in milliseconds (default: 5000)
  :retention    Name of retention policy to use (optional)
  "
  [conf measurements]
  (let [host (:host conf "localhost")
        port (:port conf "8086")
        scheme (if (:ssl? conf) "https" "http")
        path-prefix (:path-prefix conf "/")
        db (:db conf)
        write-url (cond-> (format "%s://%s:%d%swrite?db=%s" scheme host port path-prefix db)
                    (:retention conf)
                    (str "&rp=" (:retention conf)))
        http-opts (cond-> {:socket-timeout (:timeout conf 5000)
                           :conn-timeout (:timeout conf 5000)
                           :content-type "text/plain"
                           :insecure? (:insecure conf false)}
                    (:username conf)
                    (assoc :basic-auth [(:username conf) (:password conf)]))
        tags (:tags conf {})
        tags-merger #(update-in % :tags merge-keep-left tags)
        line-encoder (comp encode-measurement-line tags-merger)]
    (http/post write-url
               (assoc http-opts :body
                      (->> measurements
                           (map line-encoder)
                           (str/join "\n"))))))
