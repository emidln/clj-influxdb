# clj-influxdb

Sends measurements to InfluxDB 0.9.x+. This will not support older InfluxDB version.

This is a simple client inspired by the InfluxDB 0.9.x support in Reimann.

## Usage

An example:

```clj

    (require 'clj-influxdb.client :as influxdb)

    (influxdb/write-measurements!
      {:db "mydb"
      :tags {:env :prod}}
      [(influxdb/new-measurement "users" {:mobile true} {:total 50 :logged-in 7})
       (influxdb/new-measurement "users" {:desktop true} {:total 30 :logged-in 15})])

```

Measurements are just maps with the following keys:
 * `:measurement` - name of the measurement
 * `:tags`         - map of tag key/values
 * `:fields`       - map of field key/values
 * `:timestamp`    - timestamp in nanoseconds

 `clj-influxdb.client/new-measurement` is simple a helper to create these maps.

 See `clj-influxdb.client/write-measurements!`

## License

Copyright © 2016 Brandon Adams

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
