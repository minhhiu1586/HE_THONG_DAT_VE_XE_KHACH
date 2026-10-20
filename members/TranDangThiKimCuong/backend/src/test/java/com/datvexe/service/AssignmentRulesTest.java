package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentRulesTest {
    private ChuyenXe trip(String from,String to,LocalDateTime start,LocalDateTime end) {
        BenXe a=new BenXe(); a.maBenXe=from; BenXe b=new BenXe(); b.maBenXe=to;
        TuyenXe route=new TuyenXe(); route.tramKhoiHanh=a; route.tramDen=b;
        ChuyenXe trip=new ChuyenXe(); trip.tuyenXe=route; trip.ngayKhoiHanh=start.toLocalDate();
        trip.gioKhoiHanh=start.toLocalTime(); trip.gioDenDuKien=end; return trip;
    }
    @Test void vehiclePreparationIsSeparateFromEmployeeConnection() {
        var first=trip("A","B",LocalDateTime.of(2030,1,1,8,0),LocalDateTime.of(2030,1,1,12,0));
        var next=trip("B","A",LocalDateTime.of(2030,1,1,12,30),LocalDateTime.of(2030,1,1,17,0));
        assertDoesNotThrow(()->AssignmentRules.vehicleConnection(first,next));
        next.gioKhoiHanh=LocalTime.of(12,29);
        assertEquals(409,assertThrows(DomainException.class,()->AssignmentRules.vehicleConnection(first,next)).status());
        assertDoesNotThrow(()->AssignmentRules.connection(first,next));
    }
    @Test void differentStationRejectedEvenWhenTimeGapIsLarge() {
        var first=trip("A","B",LocalDateTime.of(2030,1,1,8,0),LocalDateTime.of(2030,1,1,12,0));
        var next=trip("C","D",LocalDateTime.of(2030,1,2,13,0),LocalDateTime.of(2030,1,2,17,0));
        assertThrows(DomainException.class,()->AssignmentRules.connection(first,next));
    }
    @Test void overnightConnectionUsesFullDateAndTime() {
        var first=trip("A","B",LocalDateTime.of(2030,1,1,22,0),LocalDateTime.of(2030,1,2,5,0));
        var next=trip("B","A",LocalDateTime.of(2030,1,2,6,0),LocalDateTime.of(2030,1,2,10,0));
        assertDoesNotThrow(()->AssignmentRules.connection(first,next));
    }
    @Test void drivingPlanRequiredOnlyAboveFourHours() {
        LocalDateTime departure = LocalDateTime.of(2030, 1, 1, 22, 0);
        var trip = trip("A", "B", departure, departure.plusHours(4));
        assertFalse(AssignmentRules.needsDrivingPlan(trip));
        trip.gioDenDuKien = departure.plusHours(4).plusSeconds(1);
        assertTrue(AssignmentRules.needsDrivingPlan(trip));
    }
}
