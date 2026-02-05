// 聊天应用全局配置
var chatConfig = {
    userId: null,
    token: null,
    currentChatId: null,
    currentChatType: null, // 'private' 或 'group'
    stompClient: null,
    apiBaseUrl: '/im',
    wsBaseUrl: window.location.protocol + '//' + window.location.host
};

// Handlebars模板编译
var chatItemTemplate = Handlebars.compile($('#chat-item-template').html());
var messageTemplate = Handlebars.compile($('#message-template').html());

// 页面加载完成后初始化
$(document).ready(function() {
    // 从URL获取token
    const urlParams = new URLSearchParams(window.location.search);
    chatConfig.token = urlParams.get('token');
    
    // 验证token
    if (!chatConfig.token) {
        alert('请先登录');
        window.location.href = '/login.html';
        return;
    }
    
    // 从token中解析userId
    try {
        const tokenParts = chatConfig.token.split('.');
        const payload = JSON.parse(atob(tokenParts[1]));
        chatConfig.userId = payload.body.userId;
        
        // 验证userId
        if (!chatConfig.userId) {
            alert('解析token获取userId失败');
            window.location.href = '/login.html';
            return;
        }
    } catch (error) {
        console.error('解析token失败:', error);
        alert('获取用户信息失败，请重新登录');
        window.location.href = '/login.html';
        return;
    }
    
    // 初始化WebSocket连接
    initWebSocket();
    
    // 加载聊天列表
    loadChatList();
    
    // 绑定聊天列表点击事件
    bindChatListEvents();
    
    // 绑定消息发送事件
    bindSendMessageEvent();
    
    // 绑定搜索功能
    bindSearchEvent();
});

// 初始化WebSocket连接
function initWebSocket() {
    try {
        // 创建WebSocket连接
        const wsUrl = `${chatConfig.wsBaseUrl}/im/private/${chatConfig.userId}?token=${chatConfig.token}`;
        chatConfig.wsClient = new WebSocket(wsUrl);
        
        // 连接打开事件
        chatConfig.wsClient.onopen = function() {
            console.log('WebSocket连接已打开');
        };
        
        // 接收消息事件
        chatConfig.wsClient.onmessage = function(event) {
            const message = JSON.parse(event.data);
            handleReceivedMessage(message);
        };
        
        // 连接关闭事件
        chatConfig.wsClient.onclose = function() {
            console.log('WebSocket连接已关闭');
            // 尝试重新连接
            setTimeout(initWebSocket, 3000);
        };
        
        // 连接错误事件
        chatConfig.wsClient.onerror = function(error) {
            console.error('WebSocket连接错误:', error);
        };
    } catch (error) {
        console.error('初始化WebSocket失败:', error);
    }
}

// 加载聊天列表
function loadChatList() {
    $.ajax({
        url: `${chatConfig.apiBaseUrl}/chat/list`,
        type: 'GET',
        headers: {
            'Authorization': `Bearer ${chatConfig.token}`
        },
        success: function(response) {
            if (response.code === 0 && response.data) {
                renderChatList(response.data);
            } else {
                console.error('加载聊天列表失败:', response.msg);
            }
        },
        error: function(xhr, status, error) {
            console.error('加载聊天列表请求失败:', error);
        }
    });
}

// 渲染聊天列表
function renderChatList(chats) {
    const chatList = $('.chat-list');
    chatList.empty();
    
    chats.forEach(chat => {
        // 格式化最后消息时间
        chat.lastMessageTime = formatTime(chat.lastMessageTime);
        
        // 渲染聊天项
        const chatItem = $(chatItemTemplate(chat));
        
        // 添加点击事件
        chatItem.click(function() {
            selectChat(chat);
        });
        
        chatList.append(chatItem);
    });
}

// 选择聊天
function selectChat(chat) {
    // 更新当前聊天信息
    chatConfig.currentChatId = chat.chatId;
    chatConfig.currentChatType = chat.type;
    
    // 更新聊天窗口头部
    $('#chat-avatar').attr('src', chat.avatar || 'img/avatar-1.jpg');
    $('#chat-name').text(chat.nickname);
    $('#chat-status').text(chat.status || '在线');
    
    // 加载聊天历史记录
    loadChatHistory(chat.chatId);
    
    // 标记为已读
    markAsRead(chat.chatId);
}

// 加载聊天历史记录
function loadChatHistory(chatId) {
    $.ajax({
        url: `${chatConfig.apiBaseUrl}/private/queryHistoryMessage`,
        type: 'GET',
        headers: {
            'Authorization': `Bearer ${chatConfig.token}`
        },
        data: {
            chatId: chatId,
            lastMessageId: 0,
            count: 50
        },
        success: function(response) {
            if (response.code === 0 && response.data && response.data.messageItemList) {
                renderMessages(response.data.messageItemList);
            } else {
                console.error('加载聊天历史记录失败:', response.msg);
            }
        },
        error: function(xhr, status, error) {
            console.error('加载聊天历史记录请求失败:', error);
        }
    });
}

// 渲染消息
function renderMessages(messages) {
    const chatMessages = $('.chat-messages');
    
    // 清空当前消息
    chatMessages.empty();
    
    // 移除欢迎信息
    $('.welcome-message').remove();
    
    // 渲染每条消息
    messages.forEach(message => {
        // 确定消息类型
        const messageType = message.senderId == chatConfig.userId ? 'sent' : 'received';
        
        // 格式化消息时间
        message.time = formatTime(message.sendTime);
        message.type = messageType;
        
        // 渲染消息
        const messageElement = $(messageTemplate(message));
        chatMessages.append(messageElement);
    });
    
    // 滚动到底部
    scrollToBottom();
}

// 绑定聊天列表事件
function bindChatListEvents() {
    // 可以在这里添加更多聊天列表相关的事件
}

// 绑定消息发送事件
function bindSendMessageEvent() {
    const sendButton = $('#send-button');
    const messageInput = $('#message-input');
    
    // 点击发送按钮发送消息
    sendButton.click(function() {
        sendMessage();
    });
    
    // 按回车键发送消息
    messageInput.keypress(function(e) {
        if (e.which == 13) { // Enter键
            sendMessage();
        }
    });
}

// 绑定搜索事件
function bindSearchEvent() {
    const searchInput = $('.search-box input');
    
    searchInput.keyup(function() {
        const keyword = $(this).val().trim();
        searchChatList(keyword);
    });
}

// 搜索聊天列表
function searchChatList(keyword) {
    const chatItems = $('.chat-item');
    
    if (!keyword) {
        chatItems.show();
        return;
    }
    
    chatItems.each(function() {
        const chatName = $(this).find('.chat-name').text();
        const lastMessage = $(this).find('.message-content').text();
        
        if (chatName.indexOf(keyword) !== -1 || lastMessage.indexOf(keyword) !== -1) {
            $(this).show();
        } else {
            $(this).hide();
        }
    });
}

// 发送消息
function sendMessage() {
    const messageInput = $('#message-input');
    const content = messageInput.val().trim();
    
    // 验证消息内容
    if (!content) {
        return;
    }
    
    // 验证当前聊天
    if (!chatConfig.currentChatId) {
        alert('请先选择一个聊天');
        return;
    }
    
    // 创建消息对象
    const message = {
        chatId: chatConfig.currentChatId,
        senderId: chatConfig.userId,
        content: content,
        type: 'TEXT',
        sendTime: new Date().getTime()
    };
    
    // 显示发送的消息
    const messageElement = $(messageTemplate({
        avatar: 'img/avatar-1.jpg',
        senderName: '我',
        content: content,
        time: formatTime(new Date().getTime()),
        type: 'sent'
    }));
    $('.chat-messages').append(messageElement);
    
    // 清空输入框
    messageInput.val('');
    
    // 滚动到底部
    scrollToBottom();
    
    // 通过WebSocket发送消息
    if (chatConfig.wsClient && chatConfig.wsClient.readyState === WebSocket.OPEN) {
        chatConfig.wsClient.send(JSON.stringify({
            type: 'SEND_PRIVATE_MESSAGE',
            data: message
        }));
    } else {
        // WebSocket连接失败，尝试通过HTTP发送
        $.ajax({
            url: `${chatConfig.apiBaseUrl}/private/message/send`,
            type: 'POST',
            headers: {
                'Authorization': `Bearer ${chatConfig.token}`,
                'Content-Type': 'application/json'
            },
            data: JSON.stringify(message),
            success: function(response) {
                if (response.code !== 0) {
                    console.error('发送消息失败:', response.msg);
                    alert('发送消息失败，请重试');
                }
            },
            error: function(xhr, status, error) {
                console.error('发送消息请求失败:', error);
                alert('发送消息失败，请检查网络连接');
            }
        });
    }
}

// 处理接收到的消息
function handleReceivedMessage(message) {
    switch (message.type) {
        case 'PRIVATE_MESSAGE_RECEIVED':
            handlePrivateMessageReceived(message.data);
            break;
        case 'GROUP_MESSAGE_RECEIVED':
            handleGroupMessageReceived(message.data);
            break;
        case 'MESSAGE_SENT_ACK':
            handleMessageSentAck(message.data);
            break;
        case 'MESSAGE_READ_ACK':
            handleMessageReadAck(message.data);
            break;
        default:
            console.log('未知消息类型:', message.type);
    }
}

// 处理私聊消息
function handlePrivateMessageReceived(message) {
    // 格式化消息时间
    message.time = formatTime(message.sendTime);
    
    // 检查是否是当前聊天
    if (message.chatId === chatConfig.currentChatId) {
        // 显示消息
        const messageElement = $(messageTemplate({
            avatar: message.avatar || 'img/avatar-1.jpg',
            senderName: message.senderName,
            content: message.content,
            time: message.time,
            type: 'received'
        }));
        $('.chat-messages').append(messageElement);
        scrollToBottom();
        
        // 标记为已读
        markAsRead(message.chatId);
    } else {
        // 更新聊天列表中的未读消息数
        updateUnreadCount(message.chatId, message.content, message.sendTime);
    }
}

// 处理群聊消息
function handleGroupMessageReceived(message) {
    // 格式化消息时间
    message.time = formatTime(message.sendTime);
    
    // 检查是否是当前聊天
    if (message.chatId === chatConfig.currentChatId) {
        // 显示消息
        const messageElement = $(messageTemplate({
            avatar: message.avatar || 'img/avatar-1.jpg',
            senderName: message.senderName,
            content: message.content,
            time: message.time,
            type: 'received'
        }));
        $('.chat-messages').append(messageElement);
        scrollToBottom();
        
        // 标记为已读
        markAsRead(message.chatId);
    } else {
        // 更新聊天列表中的未读消息数
        updateUnreadCount(message.chatId, message.content, message.sendTime);
    }
}

// 处理消息发送确认
function handleMessageSentAck(message) {
    console.log('消息发送成功:', message);
}

// 处理消息已读确认
function handleMessageReadAck(message) {
    console.log('消息已读:', message);
}

// 更新未读消息数
function updateUnreadCount(chatId, lastMessage, lastMessageTime) {
    // 查找对应的聊天项
    const chatItem = $('.chat-item').filter(function() {
        return $(this).data('chat-id') == chatId;
    });
    
    if (chatItem.length) {
        // 更新最后消息
        const messageContent = chatItem.find('.message-content');
        messageContent.text(lastMessage);
        
        // 更新最后消息时间
        const chatTime = chatItem.find('.chat-time');
        chatTime.text(formatTime(lastMessageTime));
        
        // 更新未读消息数
        const unreadCountElement = chatItem.find('.unread-count');
        if (unreadCountElement.length) {
            const currentCount = parseInt(unreadCountElement.text());
            unreadCountElement.text(currentCount + 1);
        } else {
            // 创建未读消息数元素
            const unreadCount = $('<span class="unread-count">1</span>');
            chatItem.find('.chat-last-message').append(unreadCount);
        }
        
        // 移动到列表顶部
        chatItem.prependTo('.chat-list');
    }
}

// 标记消息为已读
function markAsRead(chatId) {
    $.ajax({
        url: `${chatConfig.apiBaseUrl}/private/message/read/${chatId}`,
        type: 'POST',
        headers: {
            'Authorization': `Bearer ${chatConfig.token}`
        },
        success: function(response) {
            if (response.code !== 0) {
                console.error('标记为已读失败:', response.msg);
            }
        },
        error: function(xhr, status, error) {
            console.error('标记为已读请求失败:', error);
        }
    });
}

// 滚动到底部
function scrollToBottom() {
    const chatMessages = $('.chat-messages');
    chatMessages.scrollTop(chatMessages[0].scrollHeight);
}

// 格式化时间
function formatTime(timestamp) {
    if (!timestamp) {
        return '';
    }
    
    const date = new Date(timestamp);
    const now = new Date();
    
    // 今天的消息显示时间
    if (date.toDateString() === now.toDateString()) {
        return date.getHours() + ':' + padZero(date.getMinutes());
    }
    
    // 昨天的消息显示"昨天"
    const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    if (date.toDateString() === yesterday.toDateString()) {
        return '昨天 ' + date.getHours() + ':' + padZero(date.getMinutes());
    }
    
    // 今年的消息显示月日
    if (date.getFullYear() === now.getFullYear()) {
        return (date.getMonth() + 1) + '月' + date.getDate() + '日';
    }
    
    // 其他年份显示完整日期
    return date.getFullYear() + '年' + (date.getMonth() + 1) + '月' + date.getDate() + '日';
}

// 补零函数
function padZero(num) {
    return num < 10 ? '0' + num : num;
}

// 页面关闭时清理资源
window.onbeforeunload = function() {
    if (chatConfig.wsClient) {
        chatConfig.wsClient.close();
    }
};