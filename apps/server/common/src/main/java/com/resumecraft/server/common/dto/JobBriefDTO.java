package com.resumecraft.server.common.dto;

import lombok.Data;

@Data
public class JobBriefDTO {
    private Long id;
    private String company;
    private String title;
    private String location;
}
