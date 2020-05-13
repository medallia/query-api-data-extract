# Query API Data Extract

## Objective

This application is a reference implementation for how to stream data
from a Medallia Experience Cloud (MEC) instance using the Medallia Query
API.

## Theory of Operation

This process relies on tracking two parameters on the last survey record
pulled from the system: the *survey id* and the *initial finish date*.
The initial finish date is the time when the survey record is first
available in the Medallia Reporting interface.  For the purposes of this
process, the initial finish date must be transformed in MEC to a Unix
epoch time (in seconds).

Each pull process is started through a timer.  When the timer fires, the
current time is recorded as an end marker for the oldest record that
will be pulled; in other words, each trigger pulls all historical
records up to the point of invocation, separated into pages of up to
1,000 records per page.  Records are returned from oldest to newest.

The survey id and associated initial finish date for the last record
pulled must be persisted, to be used as a seed for the next query.

## Configurability

### Runtime

The parameters available for runtime configuration can be found in the
[application.properties.template](application.properties.template) file
at the root of the project.

Start by copying this file:

```
cp application.properties.template application.properties
```

Then edit the `application.properties` file for your particular
settings.

### Code

The `RecordProcessingService` class is the primary extension point in
the code for customizing the process to your needs.  Override these
functions:

- persistRecord()
- getLastProcessedRecord()

`RecordProcessingService#persistRecord()` persists a single record.

`RecordProcessingService#getLastProcessedRecord()` returns the
most-recently processed record that was stored.  The survey id and
initial finish date from this record are used to seed the next query.

## Dependencies

This reference implementation was built on the following dependencies:

- Java 11
- An available K-field in the Medallia system named
  `k_initialfinishdate_epoch_int` (or similar)

The code to use for `k_initialfinishdate_epoch_int` is as follows:

```javascript
(function () {
    var initialFinishDate = e_initialfinishdate;

    return (initialFinishDate == null)
        ? null
        : Math.floor(initialFinishDate.getTime() / 1000);
}());
```

## Compile/Run

Edit the `application.properties` file for your instance's settings,
then run the following commands:

```
./compile.sh

./run.sh
```

## License

Copyright 2020.  Medallia, Inc.
    
Licensed under the Apache License, Version 2.0 (the "License"); you may
not use this file except in compliance with the License.  You may obtain
a copy of the License at
    
    http://www.apache.org/licenses/LICENSE-2.0
    
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
