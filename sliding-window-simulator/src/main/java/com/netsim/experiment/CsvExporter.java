package com.netsim.experiment;

import com.netsim.sim.SimulationEvent;
import com.netsim.sim.SimulationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** CSV serialisation (RFC 4180 quoting, '.' decimal separator regardless of locale). */
public final class CsvExporter {
    private CsvExporter() { }

    public static String experimentToCsv(List<ExperimentRow> rows) {
        StringBuilder sb = new StringBuilder("protocol,loss_pct,corruption_pct,window,delay_ms,packets,trials,invalid_runs,"
                + "throughput_kbps_mean,throughput_kbps_std,delivery_ratio_pct_mean,delivery_ratio_pct_std,"
                + "retransmissions_mean,retransmissions_std,avg_delay_ms_mean,avg_delay_ms_std,"
                + "efficiency_pct_mean,efficiency_pct_std,completion_ms_mean,completion_ms_std\n");
        for (ExperimentRow r : rows) {
            sb.append(esc(r.protocol().shortName())).append(',').append(n(r.lossPct())).append(',').append(n(r.corruptionPct())).append(',')
              .append(r.window()).append(',').append(n(r.delayMs())).append(',').append(r.packets()).append(',')
              .append(r.trials()).append(',').append(r.invalidRuns());
            for (ExperimentRow.Stat s : new ExperimentRow.Stat[]{r.throughputKbps(), r.deliveryRatioPct(), r.retransmissions(),
                    r.avgDelayMs(), r.efficiencyPct(), r.completionMs()})
                sb.append(',').append(n(s.mean())).append(',').append(n(s.std()));
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String resultsToCsv(List<SimulationResult> results) {
        StringBuilder sb = new StringBuilder("protocol,status,seed,total_packets,delivered,delivery_ratio_pct,transmissions,retransmissions,"
                + "acks,lost_data,corrupted_data,lost_acks,corrupted_acks,timeouts,completion_ms,avg_delay_ms,throughput_kbps,"
                + "efficiency_pct,channel_utilization_pct\n");
        for (SimulationResult r : results) {
            sb.append(esc(r.protocol().shortName())).append(',').append(r.status()).append(',').append(r.config().getSeed()).append(',')
              .append(r.totalPackets()).append(',').append(r.delivered()).append(',').append(n(r.deliveryRatioPct())).append(',')
              .append(r.transmissions()).append(',').append(r.retransmissions()).append(',').append(r.acks()).append(',')
              .append(r.lostPackets()).append(',').append(r.corruptedPackets()).append(',').append(r.lostAcks()).append(',')
              .append(r.corruptedAcks()).append(',').append(r.timeouts()).append(',').append(n(r.completionTimeMs())).append(',')
              .append(n(r.avgDelayMs())).append(',').append(n(r.throughputKbps())).append(',').append(n(r.efficiencyPct())).append(',')
              .append(n(r.channelUtilizationPct())).append('\n');
        }
        return sb.toString();
    }

    public static String eventsToCsv(List<SimulationEvent> events) {
        StringBuilder sb = new StringBuilder("time_ms,event,packet_id,seq,attempt,detail\n");
        for (SimulationEvent e : events)
            sb.append(n(e.timeMs())).append(',').append(e.kind()).append(',').append(e.packetId()).append(',')
              .append(e.seqNum()).append(',').append(e.attempt()).append(',').append(esc(e.detail())).append('\n');
        return sb.toString();
    }

    public static void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    static String n(double v) { return Double.isNaN(v) ? "" : String.format(Locale.ROOT, "%.6f", v); }

    public static String esc(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
