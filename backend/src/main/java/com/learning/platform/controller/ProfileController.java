package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.CurrentUserService;
import com.learning.platform.service.ProfileAvatarService;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final CurrentUserService currentUser;
    private final ProfileAvatarService avatars;

    public ProfileController(CurrentUserService currentUser, ProfileAvatarService avatars) {
        this.currentUser = currentUser;
        this.avatars = avatars;
    }

    @GetMapping
    public Result<Map<String, Object>> profile(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return Result.success(avatars.profile(userId(authorization)));
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> avatar(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestPart("file") MultipartFile file
    ) throws IOException {
        // The target account comes exclusively from the signed login token.
        return Result.success(avatars.updateAvatar(userId(authorization), file));
    }

    private long userId(String authorization) {
        return ((Number) currentUser.requireLogin(authorization).get("id")).longValue();
    }
}
