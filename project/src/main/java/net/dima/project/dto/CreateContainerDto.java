package net.dima.project.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class CreateContainerDto {
    private String departurePort;
    private String arrivalPort;
    private LocalDate etd; // Estimated Time of Departure
    private LocalDate eta; // Estimated Time of Arrival
    private String size;   // "20ft" 또는 "40ft"
}