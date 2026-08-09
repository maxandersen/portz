package dk.xam.portz;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformTest {

    @Test void parseUptime_minutesSeconds() {
        assertEquals(90, Platform.parseUptime("01:30"));
    }

    @Test void parseUptime_hoursMinutesSeconds() {
        assertEquals(3661, Platform.parseUptime("1:01:01"));
    }

    @Test void parseUptime_daysHoursMinutesSeconds() {
        assertEquals(90061, Platform.parseUptime("1-01:01:01"));
    }

    @Test void parseUptime_invalid() {
        assertEquals(0, Platform.parseUptime("bogus"));
    }

    @Test void formatUptime_seconds() {
        assertEquals("45s", Platform.formatUptime(45));
    }

    @Test void formatUptime_minutes() {
        assertEquals("2m 30s", Platform.formatUptime(150));
    }

    @Test void formatUptime_hours() {
        assertEquals("1h 30m", Platform.formatUptime(5400));
    }

    @Test void formatUptime_days() {
        assertEquals("2d 3h", Platform.formatUptime(183600));
    }

    @Test void parseStatus_zombie() {
        assertEquals(ProcessStatus.ZOMBIE, Platform.parseStatus("Z", 100));
    }

    @Test void parseStatus_orphanedUnix() {
        assertEquals(ProcessStatus.ORPHANED, Platform.parseStatus("S", 1));
    }

    @Test void parseStatus_healthy() {
        assertEquals(ProcessStatus.HEALTHY, Platform.parseStatus("S", 1234));
    }
}
