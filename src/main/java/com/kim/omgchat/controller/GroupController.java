package com.kim.omgchat.controller;

import com.kim.omgchat.vo.ResultVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 16:32
 */
@RestController
@RequestMapping(value = "/group")
public class GroupController {

    @GetMapping("/listGroup")
    public ResultVO listGroup() {
        return null;
    }

    @GetMapping("/getGroup")
    public ResultVO getGroup() {
        return null;
    }

    @PostMapping("/saveGroup")
    public ResultVO saveGroup() {
        return null;
    }

    @PostMapping("/updateGroup")
    public ResultVO updateGroup() {
        return null;
    }

    @PostMapping("/removeGroup")
    public ResultVO removeGroup() {
        return null;
    }

}
