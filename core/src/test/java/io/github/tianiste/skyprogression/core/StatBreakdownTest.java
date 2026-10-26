package io.github.tianiste.skyprogression.core;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static io.github.tianiste.skyprogression.core.ProfileFixtureTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Mining Fortune / Speed totals for the sanitized Debotes · Zucchini fixture. */
class StatBreakdownTest {
    @Test void fixtureTotals() throws IOException {
        var member = member(fixture());
        var loadout = Loadout.equipped(Loadout.read(member), "TITANIUM_DRILL_4");
        assertEquals(1724.5, StatBreakdown.compute(member, loadout, StatContext.UNSPECIFIED, Stat.MINING_FORTUNE).total(), 1e-9);
        assertEquals(7050, StatBreakdown.compute(member, loadout, StatContext.in(StatContext.Zone.DWARVEN_MINES), Stat.MINING_SPEED).total(), 1e-9);
    }
}
