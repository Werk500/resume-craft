package com.resumecraft.server.job.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KeywordHit {

    private String keyword;
    private Boolean hit;
}
