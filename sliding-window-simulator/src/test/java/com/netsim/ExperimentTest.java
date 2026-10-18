package com.netsim;

import com.netsim.experiment.*;
import com.netsim.sim.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentTest {

    private static ExperimentSpec smallSpec() {
        ExperimentSpec s = new ExperimentSpec();
        s.lossPct = List.of(0.0, 20.0);
        s.corruptionPct = List.of(0.0);
        s.windows = List.of(2, 8);
        s.delaysMs = List.of(20.0);
        s.packets = List.of(30);
        s.trials = 4;
        return s;
    }

    @Test void runsEveryCombinationForBothProtocols() throws Exception {
        ExperimentSpec s = smallSpec();
        List<ExperimentRow> rows = ExperimentRunner.run(s, null, null);
        assertEquals(s.combinations() * 2, rows.size());
        assertEquals(8, rows.size());
        assertEquals(8L * 4, s.totalRuns());
    }

    @Test void losslessTrialsHaveZeroStandardDeviationAndNoRetransmissions() throws Exception {
        List<ExperimentRow> rows = ExperimentRunner.run(smallSpec(), null, null);
        for (ExperimentRow r : rows) {
            if (r.lossPct() == 0.0) {
                assertEquals(0.0, r.retransmissions().mean(), 1e-12);
                assertEquals(0.0, r.throughputKbps().std(), 1e-9);
            } else {
                assertTrue(r.retransmissions().mean() > 0);
                assertTrue(r.throughputKbps().std() > 0);
            }
            assertEquals(0, r.invalidRuns());
        }
    }

    @Test void experimentsAreReproducible() throws Exception {
        List<ExperimentRow> a = ExperimentRunner.run(smallSpec(), null, null);
        List<ExperimentRow> b = ExperimentRunner.run(smallSpec(), null, null);
        assertEquals(a, b);
    }

    @Test void statisticsUseSampleStandardDeviation() {
        ExperimentRow.Stat st = ExperimentRow.Stat.of(new double[]{2, 4, 4, 4, 5, 5, 7, 9}, 8);
        assertEquals(5.0, st.mean(), 1e-12);
        assertEquals(Math.sqrt(32.0 / 7.0), st.std(), 1e-12);
        assertEquals(0.0, ExperimentRow.Stat.of(new double[]{3}, 1).std(), 0.0);
        assertTrue(Double.isNaN(ExperimentRow.Stat.of(new double[0], 0).mean()));
    }

    @Test void invalidSpecIsRejected() {
        ExperimentSpec s = smallSpec(); s.windows = List.of();
        assertThrows(IllegalArgumentException.class, () -> ExperimentRunner.run(s, null, null));
        ExperimentSpec t = smallSpec(); t.lossPct = List.of(150.0);
        assertFalse(t.validate().isEmpty());
        ExperimentSpec u = smallSpec(); u.trials = 0;
        assertFalse(u.validate().isEmpty());
    }

    @Test void csvContainsHeaderRowsAndEscapesText() throws Exception {
        List<ExperimentRow> rows = ExperimentRunner.run(smallSpec(), null, null);
        String csv = CsvExporter.experimentToCsv(rows);
        String[] lines = csv.split("\n");
        assertEquals(rows.size() + 1, lines.length);
        assertTrue(lines[0].startsWith("protocol,loss_pct"));
        int cols = lines[0].split(",").length;
        for (String l : lines) assertEquals(cols, l.split(",", -1).length);
        assertEquals("\"a,b\"", CsvExporter.esc("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.esc("say \"hi\""));
        SimulationResult r = SimulationRunner.run(Protocol.GO_BACK_N, TestSupport.cfg(5, 2));
        assertEquals(2, CsvExporter.resultsToCsv(List.of(r)).split("\n").length);
        assertTrue(CsvExporter.eventsToCsv(r.events()).split("\n").length > 5);
    }
}
