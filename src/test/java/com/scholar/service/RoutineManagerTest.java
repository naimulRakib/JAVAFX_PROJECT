package com.scholar.service;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

public class RoutineManagerTest {

    @Test
    public void parseClassOffRequest_parsesRange() {
        String input = "9/3/2026 to 20/3/2026 varsity off";
        RoutineManager.ClassOffCommand cmd = RoutineManager.parseClassOffCommand(input);

        assertNotNull(cmd);
        assertFalse(cmd.reverse());
        assertEquals(1, cmd.ranges().size());
        assertEquals(LocalDate.of(2026, 3, 9), cmd.ranges().get(0).startDate());
        assertEquals(LocalDate.of(2026, 3, 20), cmd.ranges().get(0).endDate());
    }

    @Test
    public void parseClassOffRequest_returnsNullWithoutOffKeyword() {
        String input = "9/3/2026 to 20/3/2026 routine update";
        RoutineManager.ClassOffCommand cmd = RoutineManager.parseClassOffCommand(input);
        assertNull(cmd);
    }

    @Test
    public void parseClassOffRequest_parsesSingleDates() {
        String input = "13 Mar 2026, 17 Mar, 18 Mar 2026 varsity off";
        RoutineManager.ClassOffCommand cmd = RoutineManager.parseClassOffCommand(input);
        assertNotNull(cmd);
        assertEquals("ranges=" + cmd.ranges(), 3, cmd.ranges().size());
    }
}
