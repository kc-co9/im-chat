package com.kim.omgchat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kim.omgchat.domain.UserMessageDO;
import com.kim.omgchat.dto.UserMessageQueryDTO;
import io.swagger.models.auth.In;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/16 16:02
 */
public interface UserMessageDAO extends BaseMapper<UserMessageDO> {

    List<UserMessageDO> listFriendsMessageForXd(UserMessageQueryDTO userMessageQueryDTO);

    List<UserMessageDO> listChatMsgWithFriend(@Param("userFirId") Long userFirId ,@Param("userSecId") Long userSecId);

    Integer updateStatus(@Param("userFirId") Long fromUserId,@Param("userSecId") Long toUserId,@Param("status") int status);
}
