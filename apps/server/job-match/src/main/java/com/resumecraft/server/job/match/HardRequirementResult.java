package com.resumecraft.server.job.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HardRequirementResult {

    private double score;
    private String educationRequirement;
    private boolean educationMet;
    private String yearRequirement;
    private boolean yearMet;
    private List<String> failedItems;
    private boolean isAllMet;
}
