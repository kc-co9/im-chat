package com.co.kc.imchat.interfaces.endpoint.http;

import com.co.kc.imchat.application.GroupAppService;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupCreateCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupDismissCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupInviteMembersCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupKickMemberCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupLeaveCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupMemberAliasChangeCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupNotificationChangeCmd;
import com.co.kc.imchat.application.model.cqrs.command.group.GroupTransferOwnerCmd;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupCreateDTO;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupDetailDTO;
import com.co.kc.imchat.application.model.cqrs.dto.group.GroupItemDTO;
import com.co.kc.imchat.application.model.cqrs.query.group.GroupDetailQuery;
import com.co.kc.imchat.application.model.cqrs.query.group.GroupListQuery;
import com.co.kc.imchat.interfaces.model.io.group.GroupCreateRequest;
import com.co.kc.imchat.interfaces.model.io.group.GroupCreateResponse;
import com.co.kc.imchat.interfaces.model.io.group.GroupDismissRequest;
import com.co.kc.imchat.interfaces.model.io.group.GroupDetailResponse;
import com.co.kc.imchat.interfaces.model.io.group.GroupInviteMembersRequest;
import com.co.kc.imchat.interfaces.model.io.group.GroupKickMemberRequest;
import com.co.kc.imchat.interfaces.model.io.group.GroupLeaveRequest;
import com.co.kc.imchat.interfaces.model.io.group.GroupListResponse;
import com.co.kc.imchat.interfaces.model.io.group.GroupMemberAliasChangeRequest;
import com.co.kc.imchat.interfaces.model.io.group.GroupNotificationChangeRequest;
import com.co.kc.imchat.interfaces.model.io.group.GroupTransferOwnerRequest;
import com.co.kc.imchat.interfaces.support.context.UserContextUtils;
import com.co.kc.imchat.interfaces.transformer.GroupHttpIoTransformer;
import com.co.kc.imchat.interfaces.transformer.ImChatHttpIoTransformer;
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

    @PostMapping(value = "/kickGroupMember")
    public void kickGroupMember(@RequestBody @Validated GroupKickMemberRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        groupAppService.kickGroupMember(new GroupKickMemberCmd(userId, request.getGroupId(), request.getMemberUserId()));
    }

    @PostMapping(value = "/leaveGroup")
    public void leaveGroup(@RequestBody @Validated GroupLeaveRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        groupAppService.leaveGroup(new GroupLeaveCmd(userId, request.getGroupId()));
    }

    @PostMapping(value = "/transferGroupOwner")
    public void transferGroupOwner(@RequestBody @Validated GroupTransferOwnerRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        groupAppService.transferGroupOwner(new GroupTransferOwnerCmd(userId, request.getGroupId(), request.getNewOwnerId()));
    }

    @PostMapping(value = "/changeGroupNotification")
    public void changeGroupNotification(@RequestBody @Validated GroupNotificationChangeRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        groupAppService.changeGroupNotification(new GroupNotificationChangeCmd(userId, request.getGroupId(), request.getNotification()));
    }

    @PostMapping(value = "/changeGroupMemberAlias")
    public void changeGroupMemberAlias(@RequestBody @Validated GroupMemberAliasChangeRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        groupAppService.changeGroupMemberAlias(new GroupMemberAliasChangeCmd(userId, request.getGroupId(), request.getUserAlias()));
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
