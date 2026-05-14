package com.co.kc.imchat.endpoint.http;

import com.co.kc.imchat.application.GroupAppService;
import com.co.kc.imchat.model.cqrs.command.group.GroupCreateCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupDismissCmd;
import com.co.kc.imchat.model.cqrs.command.group.GroupInviteMembersCmd;
import com.co.kc.imchat.model.cqrs.dto.group.GroupCreateDTO;
import com.co.kc.imchat.model.cqrs.dto.group.GroupDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.group.GroupItemDTO;
import com.co.kc.imchat.model.cqrs.query.group.GroupDetailQuery;
import com.co.kc.imchat.model.cqrs.query.group.GroupListQuery;
import com.co.kc.imchat.model.io.group.GroupCreateRequest;
import com.co.kc.imchat.model.io.group.GroupCreateResponse;
import com.co.kc.imchat.model.io.group.GroupDismissRequest;
import com.co.kc.imchat.model.io.group.GroupDetailResponse;
import com.co.kc.imchat.model.io.group.GroupInviteMembersRequest;
import com.co.kc.imchat.model.io.group.GroupListResponse;
import com.co.kc.imchat.support.context.UserContextUtils;
import com.co.kc.imchat.transformer.http.GroupHttpIoTransformer;
import com.co.kc.imchat.transformer.http.ImChatHttpIoTransformer;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/im/group")
public class GroupController {
    private final GroupAppService groupAppService;

    @PostMapping(value = "/createGroup")
    public GroupCreateResponse createGroup(@RequestBody @Validated GroupCreateRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        GroupCreateCmd command = new GroupCreateCmd(userId, request.getMemberIds(), request.getGroupName());
        GroupCreateDTO dto = groupAppService.createGroup(command);
        return ImChatHttpIoTransformer.INSTANCE.groupCreateResponseFrom(dto);
    }

    @PostMapping(value = "/inviteGroupMembers")
    public void inviteGroupMembers(@RequestBody @Validated GroupInviteMembersRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        groupAppService.inviteGroupMembers(new GroupInviteMembersCmd(userId, request.getGroupId(), request.getMemberIds()));
    }

    @PostMapping(value = "/dismissGroup")
    public void dismissGroup(@RequestBody @Validated GroupDismissRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        groupAppService.dismissGroup(new GroupDismissCmd(userId, request.getGroupId()));
    }

    @GetMapping("/getGroupList")
    public GroupListResponse getGroupList() {
        Long userId = UserContextUtils.get().getUserId();
        List<GroupItemDTO> groupList = groupAppService.getGroupList(new GroupListQuery(userId));
        return new GroupListResponse(GroupHttpIoTransformer.INSTANCE.groupItemListFrom(groupList));
    }

    @GetMapping("/getGroupDetail")
    public GroupDetailResponse getGroupDetail(@RequestParam("groupId") Long groupId) {
        Long userId = UserContextUtils.get().getUserId();
        GroupDetailDTO detail = groupAppService.getGroupDetail(new GroupDetailQuery(userId, groupId));
        return GroupHttpIoTransformer.INSTANCE.groupDetailResponseFrom(detail);
    }
}
