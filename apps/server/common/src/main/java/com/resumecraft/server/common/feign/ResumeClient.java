package com.resumecraft.server.common.feign;


import com.resumecraft.server.common.dto.ResumeBriefDTO;
import com.resumecraft.server.common.dto.ResumeVersionDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "resume-service", path = "/internal")
public interface ResumeClient {

    @GetMapping("/resume/{id}")
    ResumeBriefDTO getResume(@PathVariable("id") Long id);

    @GetMapping("/version/{id}")
    ResumeVersionDTO getVersion(@PathVariable("id") Long id);
}
