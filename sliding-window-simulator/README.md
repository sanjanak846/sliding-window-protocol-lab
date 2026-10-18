# Sliding Window Protocol Lab

Interactive analysis and **requirement-based selection** of Go-Back-N (GBN) and Selective Repeat (SR),
built on a discrete-event network simulator. Java 21, JavaFX 21, Maven, JUnit 5.

## Build, run, test

```
mvn clean javafx:run      # launches the GUI (JavaFX is pulled in by Maven; nothing to configure)
mvn test                  # runs the JUnit 5 suite (no GUI needed)
```
Requires JDK 21 and Maven 3.9+. The first build needs internet access to download dependencies.

### IntelliJ IDEA
1. *File > Open*, choose the project's `pom.xml`, "Open as Project". Let Maven import finish.
2. *File > Project Structure*: Project SDK = JDK 21, language level 21.
3. Run the **`com.netsim.ui.Launcher`** class (right-click > Run). `Launcher` does not extend `Application`,
   which avoids the "JavaFX runtime components are missing" error. Alternatively use the Maven tool window:
   *Plugins > javafx > javafx:run*.
4. Tests: right-click `src/test/java` > *Run 'All Tests'*.

## Using the application
A shared **configuration panel** (left) feeds four modes (tabs):

| Tab | Purpose |
|---|---|
| Simulator | Run one protocol; animated replay (Start / Pause / Resume / Step / Reset, speed slider), live event log, 15 metric cards, CSV export. |
| Compare | GBN vs SR on identical conditions: metric table, five bar charts, generated explanation, CSV export. |
| Recommend | Hard requirements + priority weights -> decision (GBN / SR / tie / neither) with per-constraint PASS/FAIL and measured values. |
| Experiments | Sweep loss, corruption, window, delay and packet count (editable comma-separated lists), repeated trials with mean +/- sd, line chart, table, CSV export. Runs on a background thread with progress and Cancel. |

All inputs are validated; errors are listed in a dialog and under the form. Units are in the labels
(probabilities in %, delays in ms, data rate in Mbit/s, payload in bytes).

## Architecture
```
com.netsim.sim        discrete-event kernel (no GUI dependency)
  SimulationConfig    parameters + validation          EventQueue / EventType   priority queue + tie-break order
  NetworkSimulator    clock, link, timers, ACK path    Channel                  loss/corruption model (counter-based hash)
  Packet, Ack, Fate   value types                      SimulationEvent          log entry the GUI replays
  RawStats, SimulationResult, SimulationRunner, Status, Protocol
com.netsim.protocol   ProtocolEngine (interface), GoBackNEngine, SelectiveRepeatEngine
com.netsim.analysis   PerformanceAnalyzer (metrics), Requirements, RecommendationEngine, ...
com.netsim.experiment ExperimentSpec, ExperimentRunner (thread pool), ExperimentRow, CsvExporter
com.netsim.ui         MainApp, Launcher, ConfigPanel, SimulatorTab, ComparisonTab, RecommendationTab,
                      ExperimentTab, PacketVisualizer, MetricsPanel, ChartController, Fx
```
Dependencies point one way: `ui -> experiment/analysis -> protocol -> sim`. The GUI only *reads* the
`SimulationEvent` list; the visualizer cannot alter protocol behaviour.

## Modeling assumptions
* **Time** is simulated, in integer microseconds. No `Thread.sleep`; wall-clock time never enters the model.
* **Link**: one sender link serialises transmissions (tx delay = payload bits / data rate), then a one-way
  propagation delay. ACKs take `ACK delay + propagation` and are never queued.
* **Fault model**: each transmission is lost with probability `p_loss`; if it survives it is corrupted with
  probability `p_corrupt`. A corrupted packet/ACK *arrives* and is discarded by its receiver (checksum
  failure) - no ACK is generated for a corrupted data packet. ACKs suffer the same rates unless the
  "apply to ACKs" box is cleared.
* **Common random numbers**: the fate of data transmission *(packet id, attempt)* is a hash of
  `(seed, id, attempt)`; an ACK's fate is a hash of the data transmission that triggered it. Both protocols
  therefore see identical channel luck for identical transmissions, and a seed reproduces a run exactly.
* **Event order at equal timestamps**: DATA_ARRIVAL, ACK_ARRIVAL, TRANSMIT, TIMER, then insertion order
  (FIFO). So an ACK arriving exactly when a timer expires cancels the timeout.
* **Timers** start when the packet's last bit leaves the sender. Stale timers are invalidated with a
  generation counter and counted/logged as `STALE_TIMER`.
* **Termination**: COMPLETED (all ACKed), ABORTED_RETRY_LIMIT (a packet exceeded *max retransmissions*;
  makes delivery ratio < 100 % meaningful), ABORTED_EVENT_LIMIT and STALLED (flagged **invalid**; the
  recommendation engine always disqualifies them).
* Headers are ignored: only payload bits are counted.

## Protocols
**Go-Back-N** - window W, sequence space W+1, base/next pointers, *one* timer for the oldest unacked
packet, cumulative ACKs. On timeout the whole outstanding window `base..next-1` is resent. Receiver keeps
`expectedSeq`, accepts only that packet, discards everything else and re-ACKs the last in-order packet
(nothing is sent before the first packet arrives). A cumulative ACK restarts the timer if packets remain
outstanding, stops it otherwise. Duplicate/old ACKs map to no outstanding packet and are ignored.

**Selective Repeat** - window W, sequence space 2W, per-packet timers, individual ACKs. Only timed-out,
unacked packets are resent. Receiver window W starting at `rcvBase`: packets inside it are ACKed and
buffered, the contiguous prefix is delivered in order; packets from the previous window are re-ACKed
(so a lost ACK cannot stall the sender); anything else is ignored. With M = 2W, `(seq - rcvBase) mod M`
cleanly separates "new window" (< W) from "old window" (>= W).

Both engines work on sequence numbers modulo M on the wire; the absolute packet index exists only on the
sender (to resolve ACKs unambiguously) and in the metrics.

## Metrics (full detail: docs/METRICS.md)
* Delivery ratio = delivered unique packets / generated x 100.
* Retransmissions = data transmission attempts beyond each packet's first.
* Avg delivery delay = mean over delivered packets of (in-order delivery time - first transmission start);
  includes time waiting for retransmissions. `n/a` (NaN) if nothing is delivered.
* Throughput = delivered payload bits / completion time (0 if completion time is 0 or nothing delivered).
* Protocol efficiency = delivered payload bits / transmitted payload bits x 100 (= delivered / attempts).
* Channel utilization = sender-link busy time (all attempts x tx delay) / completion time, capped at 100 %.
  **Distinct from efficiency**: efficiency measures wasted *content*, utilization measures a busy *link*.

## Recommendation algorithm (full detail: docs/RECOMMENDATION.md)
Validate -> simulate both with identical seed/conditions -> check every ticked **hard** constraint ->
eliminate violators -> one survivor wins -> two survivors are ranked by the weighted normalised score
(tie if scores differ by less than the tie margin) -> none survive: report NEITHER with the violated
constraints. The score is never used to override a violated hard constraint.

## Tests
`mvn test` runs 59 JUnit 5 tests in `src/test/java/com/netsim`: channel statistics, event ordering,
validation, all twelve required scenarios (single packet, lossless multi-packet, lost data, lost ACK, high
loss, high delay, small/large windows, corruption, and the three recommendation outcomes), timer
staleness, simultaneous events, window advancement, a randomized wraparound fuzz test (400 runs: every
packet delivered exactly once, in order), termination, reproducibility and metric definitions. Expected
timings are derived by hand from the model (e.g. a single lossless packet completes in
8 + 50 + 1 + 50 = 109 ms) rather than hardcoded from observed output.

## Sample experiments
See `samples/experiments.md` for value lists to paste into the Experiments tab.

## Limitations
Single unidirectional flow; no header overhead, no bandwidth sharing, no ACK queueing; fixed timeout
(no RTT estimation); fixed-size packets. Results are for the simulated model, not a real network.
