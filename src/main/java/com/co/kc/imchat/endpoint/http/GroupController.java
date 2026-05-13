package com.co.kc.imchat.endpoint.http;

import com.co.kc.imchat.application.GroupAppService;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupCreateCmd;
import com.co.kc.imchat.model.cqrs.command.chat.ImGroupInviteMembersCmd;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupCreateDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupDetailDTO;
import com.co.kc.imchat.model.cqrs.dto.im.ImGroupItemDTO;
import com.co.kc.imchat.model.cqrs.query.ImGroupDetailQuery;
import com.co.kc.imchat.model.cqrs.query.ImGroupListQuery;
import com.co.kc.imchat.model.io.chat.ImGroupCreateRequest;
import com.co.kc.imchat.model.io.chat.ImGroupCreateResponse;
import com.co.kc.imchat.model.io.chat.ImGroupDetailResponse;
import com.co.kc.imchat.model.io.chat.ImGroupInviteMembersRequest;
import com.co.kc.imchat.model.io.chat.ImGroupListResponse;
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
    public ImGroupCreateResponse createGroup(@RequestBody @Validated ImGroupCreateRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupCreateCmd command = new ImGroupCreateCmd(userId, request.getMemberIds(), request.getGroupName());
        ImGroupCreateDTO dto = groupAppService.createGroup(command);
        return ImChatHttpIoTransformer.INSTANCE.imGroupCreateResponseFrom(dto);
    }

    @PostMapping(value = "/inviteGroupMembers")
    public void inviteGroupMembers(@RequestBody @Validated ImGroupInviteMembersRequest request) {
        Long userId = UserContextUtils.get().getUserId();
        groupAppService.inviteGroupMembers(new ImGroupInviteMembersCmd(userId, request.getGroupId(), request.getMemberIds()));
    }

    @GetMapping("/getGroupList")
    public ImGroupListResponse getGroupList() {
        Long userId = UserContextUtils.get().getUserId();
        List<ImGroupItemDTO> groupList = groupAppService.getGroupList(new ImGroupListQuery(userId));
        return new ImGroupListResponse(GroupHttpIoTransformer.INSTANCE.imGroupItemListFrom(groupList));
    }

    @GetMapping("/getGroupDetail")
    public ImGroupDetailResponse getGroupDetail(@RequestParam("groupId") Long groupId) {
        Long userId = UserContextUtils.get().getUserId();
        ImGroupDetailDTO detail = groupAppService.getGroupDetail(new ImGroupDetailQuery(userId, groupId));
        return GroupHttpIoTransformer.INSTANCE.imGroupDetailResponseFrom(detail);
    }
}
