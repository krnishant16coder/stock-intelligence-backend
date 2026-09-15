package com.stockintelligence.schedule;

public record ScheduleRequest(Frequency frequency, String timezone, Boolean active) {}
