<template>
  <div class="chat-container">
    <!-- 左侧聊天列表 -->
    <div class="chat-sidebar">
      <!-- 顶部工具栏 -->
      <div class="sidebar-header">
        <div class="user-info">
          <img src="/img/avatar-1.jpg" alt="我的头像" class="avatar">
          <span class="username">我</span>
        </div>
        <div class="sidebar-tools">
          <button class="tool-btn"><i class="fa fa-search"></i></button>
          <button class="tool-btn"><i class="fa fa-plus"></i></button>
          <button class="tool-btn"><i class="fa fa-ellipsis-v"></i></button>
        </div>
      </div>
      
      <!-- 搜索框 -->
      <div class="search-box">
        <i class="fa fa-search"></i>
        <input type="text" v-model="searchKeyword" placeholder="搜索">
      </div>
      
      <!-- 聊天列表标签 -->
      <div class="chat-tabs">
        <div class="tab" :class="{ active: activeTab === '消息' }" @click="switchTab('消息')">消息</div>
        <div class="tab" :class="{ active: activeTab === '联系人' }" @click="switchTab('联系人')">联系人</div>

      </div>
      
      <!-- 聊天列表 -->
      <div class="chat-list">
        <div v-if="activeTab === '消息'">
          <div v-for="chat in filteredChats" :key="chat.chatId" 
               class="chat-item" 
               :class="{ active: currentChatId === chat.chatId }"
               @click="selectChat(chat)">
            <img :src="'/img/avatar-' + (chat.chatId % 6 + 1) + '.jpg'" :alt="chat.chatName" class="avatar">
            <div class="chat-info">
              <div class="chat-header-info">
                <span class="chat-name">{{ chat.chatName || '未知用户' }}</span>
                <span class="chat-time">{{ chat.lastMessageTime ? formatTime(chat.lastMessageTime) : formatTime(new Date().getTime()) }}</span>
              </div>
              <div class="chat-last-message">
                <span class="message-content">{{ chat.lastMessage || '点击进入聊天' }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-else-if="activeTab === '联系人'">
          <div v-for="contact in contacts" :key="contact.userId" 
               class="chat-item" 
               @click="selectContact(contact)">
            <img :src="'/img/avatar-' + (contact.userId % 6 + 1) + '.jpg'" :alt="contact.alias" class="avatar">
            <div class="chat-info">
              <div class="chat-header-info">
                <span class="chat-name">{{ contact.alias || '未知联系人' }}</span>
              </div>
              <div class="chat-last-message">
                <span class="message-content">添加于: {{ formatTime(new Date(contact.createTime).getTime()) }}</span>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>

    <!-- 右侧聊天窗口 -->
    <div class="chat-main">
      <!-- 聊天窗口头部 -->
      <div v-if="currentChat" class="chat-header">
        <div class="user-info">
          <img :src="'/img/avatar-' + (currentChat.chatId % 6 + 1) + '.jpg'" :alt="currentChat.chatName" class="avatar" id="chat-avatar">
          <div class="user-details">
            <h3 id="chat-name">{{ currentChat.chatName }}</h3>
            <span id="chat-status">{{ currentChat.chatType === 'PRIVATE' ? '私聊' : '群聊' }}</span>
          </div>
        </div>
        <div class="chat-tools">
          <button class="tool-btn"><i class="fa fa-phone"></i></button>
          <button class="tool-btn"><i class="fa fa-video-camera"></i></button>
          <button class="tool-btn"><i class="fa fa-ellipsis-v"></i></button>
        </div>
      </div>
      <div v-else class="chat-header">
        <div class="user-info">
          <img src="" alt="" class="avatar" id="chat-avatar">
          <div class="user-details">
            <h3 id="chat-name">选择一个聊天</h3>
            <span id="chat-status">离线</span>
          </div>
        </div>
      </div>

      <!-- 聊天消息区域 -->
      <div class="chat-messages" ref="chatMessages">
        <div v-if="!currentChat" class="welcome-message">
          <h2>欢迎使用即时通讯</h2>
          <p>选择一个聊天开始对话</p>
        </div>
        <div v-else>
          <div v-for="message in messages" :key="message.messageId" 
               class="message" 
               :class="{ sent: message.senderId === userId, received: message.senderId !== userId }">
            <img :src="message.senderId === userId ? '/img/avatar-1.jpg' : '/img/avatar-' + (message.senderId % 6 + 2) + '.jpg'" 
                 :alt="'用户' + message.senderId" 
                 class="avatar">
            <div class="message-content">
              <div class="message-text">{{ message.content }}</div>
              <div class="message-time">{{ formatTime(new Date(message.sendTime).getTime()) }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 消息输入区域 -->
      <div v-if="currentChat" class="chat-input-area">
        <div class="input-tools">
          <button class="tool-btn"><i class="fa fa-smile-o"></i></button>
          <button class="tool-btn"><i class="fa fa-paperclip"></i></button>
          <button class="tool-btn"><i class="fa fa-image"></i></button>
        </div>
        <div class="input-container">
          <input type="text" v-model="messageContent" 
                 placeholder="输入消息..." 
                 @keypress.enter="sendMessage">
        </div>
        <div class="send-button-container">
          <button class="send-btn" @click="sendMessage">发送</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import SockJS from 'sockjs-client'
import Stomp from 'stompjs'
import { API_CONFIG } from '../config/api.js';

export default {
  name: 'Chat',
  data() {
    return {
      // 用户信息
      userId: null,
      token: null,
      
      // 当前聊天信息
      currentChatId: null,
      currentChatType: null,
      currentChat: null,
      
      // 聊天列表数据
      chats: [],
      searchKeyword: '',
      activeTab: '消息',
      
      // 联系人列表数据
      contacts: [],
      

      
      // 消息数据
      messages: [],
      messageContent: '',
      
      // WebSocket连接
      stompClient: null,
      reconnectAttempts: 0,
      // 使用API配置
      apiConfig: API_CONFIG
    }
  },
  computed: {
    // 过滤后的聊天列表
    filteredChats() {
      if (!this.searchKeyword) {
        return this.chats;
      }
      var keyword = this.searchKeyword.toLowerCase();
      return this.chats.filter(chat => {
        var chatName = (chat.chatName || '').toLowerCase();
        return chatName.indexOf(keyword) !== -1;
      });
    }
  },
  created() {
    this.initApp();
  },
  methods: {
    // 初始化应用
    initApp() {
      // 记录当前URL
      console.log('当前URL:', window.location.href);
      console.log('路由对象:', this.$route);
      console.log('路由query:', this.$route.query);
      
      // 使用Vue Router获取token参数（正确处理hash路由）
      this.token = this.$route.query.token;
      
      console.log('从路由获取到的token:', this.token);
      console.log('token类型:', typeof this.token);
      console.log('token长度:', this.token ? this.token.length : 0);
      
      // 验证token
      if (!this.token) {
        console.error('路由中没有找到token参数');
        alert('请先登录');
        this.$router.push('/login');
        return;
      }
      
      // 验证token格式是否正确
      var tokenParts = this.token.split('.');
      if (tokenParts.length !== 3) {
        console.error('token格式不正确，应该包含3个部分');
        alert('token格式不正确，请重新登录');
        this.$router.push('/login');
        return;
      }
      
      console.log('token格式验证通过');
      console.log('token第1部分长度:', tokenParts[0].length);
      console.log('token第2部分长度:', tokenParts[1].length);
      console.log('token第3部分长度:', tokenParts[2].length);
      
      // 从token中解析userId
      try {
        // 使用已经验证过格式的tokenParts
        // 解析第2部分（payload）
        var payloadBase64 = tokenParts[1];
        console.log('payloadBase64:', payloadBase64);
        
        // 填充base64字符串
        while (payloadBase64.length % 4 !== 0) {
          payloadBase64 += '=';
        }
        
        var payload = JSON.parse(atob(payloadBase64));
        console.log('解析后的payload:', JSON.stringify(payload, null, 2));
        
        // 注意：token中的body可能是字符串形式，需要再次解析
        var body = payload.body;
        console.log('body:', body);
        console.log('body类型:', typeof body);
        
        if (typeof body === 'string') {
          body = JSON.parse(body);
          console.log('解析后的body:', JSON.stringify(body, null, 2));
        }
        
        this.userId = body.userId;
        
        // 验证userId
        if (!this.userId) {
          console.error('userId为空');
          alert('解析token获取userId失败');
          this.$router.push('/login');
          return;
        }
        
        console.log('解析token成功，userId:', this.userId);
      } catch (error) {
        console.error('解析token失败:', error);
        console.error('错误类型:', error.name);
        console.error('错误信息:', error.message);
        console.error('token:', this.token);
        alert('获取用户信息失败，请重新登录: ' + error.message);
        this.$router.push('/login');
        return;
      }
      
      // 初始化WebSocket连接
      this.initWebSocket();
      
      // 加载聊天列表
      this.loadChatList();
    },
    
    // 初始化WebSocket连接（使用STOMP协议）
    initWebSocket() {
      if (this.stompClient && this.stompClient.connected) {
        return; // 如果已经连接，不再重复连接
      }
      
      try {
        // 创建SockJS连接，配置选项以减少请求频率
        var socket = new SockJS(this.apiConfig.ws.web + '?token=' + this.token, null, {
          transports: ['websocket', 'xhr_streaming'], // 优先使用websocket，其次是xhr_streaming
          timeout: 10000, // 连接超时时间（毫秒）
          server: 30000, // 服务器超时时间（毫秒）
          sessionId: function() { return Math.random().toString(36).substring(2, 15); } // 自定义sessionId生成
        });
        
        // 创建STOMP客户端
        this.stompClient = Stomp.over(socket);
        
        // 配置STOMP客户端
        this.stompClient.debug = null; // 关闭调试日志，减少控制台输出
        this.stompClient.heartbeat.incoming = 60000; // 增加客户端接收心跳间隔（毫秒）
        this.stompClient.heartbeat.outgoing = 60000; // 增加客户端发送心跳间隔（毫秒）
        this.stompClient.reconnect_delay = 5000; // 设置重连延迟（毫秒）
        
        // 连接参数
        var headers = {
          'token': this.token
        };
        
        // 连接成功回调
        var self = this;
        var onConnected = function() {
          console.log('STOMP连接已打开');
          self.reconnectAttempts = 0; // 重置重连尝试次数
          
          // 订阅私人消息发送频道
          self.stompClient.subscribe('/queue/message/private/sent', function(message) {
            var messageBody = JSON.parse(message.body);
            self.handleReceivedMessage(messageBody);
          });
          
          // 订阅私人消息读取频道
          self.stompClient.subscribe('/queue/message/private/read', function(message) {
            var messageBody = JSON.parse(message.body);
            self.handleMessageRead(messageBody);
          });
          
          // 订阅私人消息撤回频道
          self.stompClient.subscribe('/queue/message/private/revoked', function(message) {
            var messageBody = JSON.parse(message.body);
            self.handleMessageRevoked(messageBody);
          });
        };
        
        // 连接错误回调
        var onError = function(error) {
          console.error('STOMP连接错误:', error);
        };
        
        // 连接关闭回调
        var onClose = function() {
          console.log('STOMP连接已关闭');
          // 尝试重连，但限制重连次数和频率
          if (self.reconnectAttempts < 5) {
            self.reconnectAttempts++;
            var delay = Math.min(1000 * Math.pow(2, self.reconnectAttempts), 30000); // 指数退避，最大30秒
            setTimeout(function() {
              console.log('尝试重连...', self.reconnectAttempts, '延迟', delay, 'ms');
              self.initWebSocket();
            }, delay);
          } else {
            console.log('已达到最大重连次数，停止重连');
          }
        };
        
        // 建立连接
        this.stompClient.connect(headers, onConnected, function(error) {
          onError(error);
          onClose();
        });
      } catch (error) {
        console.error('初始化STOMP WebSocket失败:', error);
      }
    },
    
    // 加载聊天列表
    loadChatList() {
      console.log('开始加载聊天列表');
      console.log('API地址:', this.apiConfig.chat.getChatList);
      console.log('token:', this.token);
      
      fetch(this.apiConfig.chat.getChatList, {
        method: 'GET', // 确保使用正确的HTTP方法
        headers: {
          'token': this.token
        }
        // 移除credentials: 'include'，避免与token认证冲突
      })
      .then(response => response.json())
      .then(response => {
        if (response.code === 0 && response.data && response.data.chatList) {
          this.chats = response.data.chatList;
        } else {
          console.error('加载聊天列表失败:', response.msg);
        }
      })
      .catch(error => {
        console.error('加载聊天列表请求失败:', error);
        console.error('错误类型:', error.name);
        console.error('错误信息:', error.message);
        console.error('完整错误:', JSON.stringify(error));
      });
    },
    
    // 切换标签页
    switchTab(tabType) {
      this.activeTab = tabType;
      
      // 根据标签页类型加载对应数据
      if (tabType === '联系人') {
        this.loadContacts();

      }
    },
    
    // 加载联系人列表
    loadContacts() {
      // 如果已经加载过联系人列表，直接返回
      if (this.contacts.length > 0) {
        return;
      }
      
      fetch(this.apiConfig.friend.friendList, {
        headers: {
          'token': this.token
        }
        // 移除credentials: 'include'，避免与token认证冲突
      })
      .then(response => response.json())
      .then(response => {
        if (response.code === 0 && response.data && response.data.friends) {
          this.contacts = response.data.friends;
        } else {
          console.error('加载联系人列表失败:', response.msg);
          // 不使用模拟数据，显示空列表或错误信息
          this.contacts = [];
        }
      })
      .catch(error => {
        console.error('加载联系人列表请求失败:', error);
        // 不使用模拟数据，显示空列表或错误信息
        this.contacts = [];
      });
    },
    
    // 选择联系人
    selectContact(contact) {
      // 根据联系人信息创建或打开聊天
      fetch(this.apiConfig.chat.enterPrivateChat, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'token': this.token
        },
        body: JSON.stringify({
          receiverId: contact.userId
        })
      })
      .then(response => response.json())
      .then(response => {
        if (response.code === 0 && response.data && response.data.chatId) {
          // 切换到消息标签页并选择该聊天
          this.activeTab = '消息';
          // 查找该聊天是否已在聊天列表中
          var chat = this.chats.find(c => c.chatId === response.data.chatId);
          if (chat) {
            this.selectChat(chat);
          } else {
            // 如果不在列表中，重新加载聊天列表
            this.loadChatList();
          }
        } else {
          console.error('创建聊天失败:', response.msg);
        }
      })
      .catch(error => {
        console.error('创建聊天请求失败:', error);
      });
    },
    

    
    // 选择聊天
    selectChat(chat) {
      this.currentChatId = chat.chatId;
      this.currentChatType = chat.chatType;
      this.currentChat = chat;
      
      // 加载聊天历史记录
      this.loadChatHistory(chat.chatId);
      
      // 标记为已读
      this.markAsRead(chat.chatId);
    },
    
    // 加载聊天历史记录
    loadChatHistory(chatId) {
      // 清空当前消息列表
      this.messages = [];
      
      fetch(this.apiConfig.message.queryHistoryMessage + '?chatId=' + chatId + '&lastMessageId=0&count=50', {
        headers: {
          'token': this.token
        }
        // 移除credentials: 'include'，避免与token认证冲突
      })
      .then(response => response.json())
      .then(response => {
        if (response.code === 0 && response.data && response.data.messageList) {
          this.messages = response.data.messageList;
          this.$nextTick(() => {
            this.scrollToBottom();
          });
        } else {
          console.error('加载聊天历史记录失败:', response.msg);
        }
      })
      .catch(error => {
        console.error('加载聊天历史记录请求失败:', error);
      });
    },
    
    // 发送消息
    sendMessage() {
      var content = this.messageContent.trim();
      if (!content) {
        return;
      }
      
      // 验证当前聊天
      if (!this.currentChatId) {
        alert('请先选择一个聊天');
        return;
      }
      
      // 创建消息对象
      var message = {
        chatId: this.currentChatId,
        senderId: this.userId,
        content: content,
        type: 'TEXT',
        sendTime: new Date().getTime()
      };
      
      // 添加到消息列表
      this.messages.push({
        ...message
      });
      
      // 清空输入框
      this.messageContent = '';
      
      // 滚动到底部
      this.scrollToBottom();
      
      // 使用WebSocket发送消息
      if (this.stompClient) {
        this.stompClient.send('/app/im/private/send', {
          'token': this.token
        }, JSON.stringify(message));
      }
    },
    
    // 处理接收到的消息
    handleReceivedMessage(message) {
      // 如果是当前聊天，直接添加到消息列表
      if (message.chatId === this.currentChatId) {
        this.messages.push(message);
        this.scrollToBottom();
        this.markAsRead(message.chatId);
      } else {
        // 更新聊天列表中的消息
        this.updateChatList(message);
      }
    },
    
    // 处理系统消息
    handleSystemMessage(message) {
      console.log('收到系统消息:', message);
    },
    
    // 更新聊天列表
    updateChatList(message) {
      // 查找对应的聊天
      var chatIndex = this.chats.findIndex(chat => chat.chatId === message.chatId);
      
      if (chatIndex !== -1) {
        // 更新聊天信息
        this.chats[chatIndex].lastMessage = message.content;
        this.chats[chatIndex].lastMessageTime = message.sendTime;
        
        // 将聊天移到列表顶部
        var chat = this.chats.splice(chatIndex, 1)[0];
        this.chats.unshift(chat);
      }
    },
    
    // 处理消息已读
    handleMessageRead(message) {
      // 更新消息的已读状态
      if (message.chatId === this.currentChatId) {
        this.messages.forEach(msg => {
          if (msg.messageId === message.messageId) {
            msg.readStatus = 'READ';
          }
        });
      }
    },
    
    // 处理消息撤回
    handleMessageRevoked(message) {
      // 更新消息的撤回状态
      if (message.chatId === this.currentChatId) {
        this.messages.forEach(msg => {
          if (msg.messageId === message.messageId) {
            msg.revoked = true;
            msg.content = '[消息已撤回]';
          }
        });
      }
    },
    
    // 标记为已读
    markAsRead(chatId) {
      // 调用API标记为已读
      fetch(this.apiConfig.chat.markAsRead, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'token': this.token
        },
        body: JSON.stringify({
          chatId: chatId
        })
        // 移除credentials: 'include'，避免与token认证冲突
      })
      .then(response => response.json())
      .then(response => {
        if (response.code !== 0) {
          console.error('标记消息已读失败:', response.msg);
        }
      })
      .catch(error => {
        console.error('标记消息已读请求失败:', error);
      });
    },
    
    // 滚动到底部
    scrollToBottom() {
      var chatMessages = this.$refs.chatMessages;
      if (chatMessages) {
        chatMessages.scrollTop = chatMessages.scrollHeight;
      }
    },
    
    // 格式化时间
    formatTime(timestamp) {
      if (!timestamp) {
        return '';
      }
      
      var date = new Date(timestamp);
      var now = new Date();
      
      // 今天的消息显示时间
      if (date.toDateString() === now.toDateString()) {
        return this.formatDate(date, 'HH:mm');
      }
      
      // 昨天的消息显示"昨天"
      var yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
      if (date.toDateString() === yesterday.toDateString()) {
        return '昨天 ' + this.formatDate(date, 'HH:mm');
      }
      
      // 今年的消息显示月日
      if (date.getFullYear() === now.getFullYear()) {
        return this.formatDate(date, 'MM-dd HH:mm');
      }
      
      // 其他年份显示完整日期
      return this.formatDate(date, 'yyyy-MM-dd HH:mm');
    },
    
    // 格式化日期
    formatDate(date, fmt) {
      var o = {
        'M+': date.getMonth() + 1, //月份
        'd+': date.getDate(), //日
        'H+': date.getHours(), //小时
        'm+': date.getMinutes(), //分
        's+': date.getSeconds(), //秒
        'q+': Math.floor((date.getMonth() + 3) / 3), //季度
        'S': date.getMilliseconds() //毫秒
      };
      
      if (/(y+)/.test(fmt)) {
        fmt = fmt.replace(RegExp.$1, (date.getFullYear() + '').substr(4 - RegExp.$1.length));
      }
      
      for (var k in o) {
        if (new RegExp('(' + k + ')').test(fmt)) {
          fmt = fmt.replace(RegExp.$1, (RegExp.$1.length === 1) ? (o[k]) : (('00' + o[k]).substr(('' + o[k]).length)));
        }
      }
      
      return fmt;
    },
    

  },
  beforeDestroy() {
    // 断开WebSocket连接
    if (this.stompClient) {
      this.stompClient.disconnect();
    }
  }
}
</script>

<style scoped>
/* 聊天页面样式 */
.chat-container {
  display: flex;
  height: 100vh;
  background-color: #f5f5f5;
}

.chat-sidebar {
  width: 300px;
  background-color: #fff;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border-bottom: 1px solid #e0e0e0;
}

.user-info {
  display: flex;
  align-items: center;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-right: 10px;
}

.username {
  font-weight: 500;
}

.sidebar-tools {
  display: flex;
}

.tool-btn {
  background: none;
  border: none;
  margin-left: 10px;
  font-size: 18px;
  cursor: pointer;
  color: #666;
}

.search-box {
  position: relative;
  padding: 10px;
  border-bottom: 1px solid #e0e0e0;
}

.search-box i {
  position: absolute;
  left: 20px;
  top: 50%;
  transform: translateY(-50%);
  color: #999;
}

.search-box input {
  width: 100%;
  padding: 8px 10px 8px 35px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  outline: none;
}

.chat-tabs {
  display: flex;
  background-color: #fff;
  border-bottom: 1px solid #e0e0e0;
}

.tab {
  flex: 1;
  text-align: center;
  padding: 12px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.tab.active {
  color: #07c160;
  border-bottom: 2px solid #07c160;
  font-weight: 500;
}

.chat-list {
  flex: 1;
  overflow-y: auto;
}

.chat-item {
  display: flex;
  padding: 10px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background-color 0.2s;
}

.chat-item:hover {
  background-color: #f5f5f5;
}

.chat-item.active {
  background-color: #e8f0fe;
}

.chat-info {
  flex: 1;
  overflow: hidden;
}

.chat-header-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}

.chat-name {
  font-weight: 500;
  color: #333;
}

.chat-time {
  font-size: 12px;
  color: #999;
}

.chat-last-message {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #666;
  overflow: hidden;
}

.message-content {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread-count {
  background-color: #ff6b6b;
  color: #fff;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 10px;
  margin-left: 10px;
}

/* 联系人列表样式 */
.dynamic-item {
  padding: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.dynamic-header {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.dynamic-user-info {
  flex: 1;
}

.dynamic-username {
  font-weight: 500;
  margin-right: 10px;
}

.dynamic-time {
  font-size: 12px;
  color: #999;
}

.dynamic-content {
  margin-bottom: 10px;
}

.dynamic-content p {
  margin: 0 0 10px 0;
}

.dynamic-image {
  max-width: 100%;
  border-radius: 8px;
}

.dynamic-actions {
  display: flex;
  border-top: 1px solid #f0f0f0;
  padding-top: 10px;
}

.action-btn {
  background: none;
  border: none;
  margin-right: 20px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
}

/* 右侧聊天窗口 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  background-color: #fff;
  border-bottom: 1px solid #e0e0e0;
}

.chat-tools {
  display: flex;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #fafafa;
}

.welcome-message {
  text-align: center;
  margin-top: 100px;
  color: #999;
}

.message {
  display: flex;
  margin-bottom: 15px;
  max-width: 70%;
}

.message.sent {
  flex-direction: row-reverse;
  margin-left: auto;
}

.message.received {
  margin-right: auto;
}

.message .avatar {
  width: 35px;
  height: 35px;
}

.message-content {
  max-width: calc(100% - 50px);
}

.message-text {
  background-color: #fff;
  padding: 10px 15px;
  border-radius: 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  word-wrap: break-word;
}

.message.sent .message-text {
  background-color: #07c160;
  color: #fff;
}

.message-time {
  font-size: 12px;
  color: #999;
  text-align: right;
  margin-top: 5px;
  margin-right: 5px;
}

.message.received .message-time {
  text-align: left;
  margin-left: 5px;
}

/* 消息输入区域 */
.chat-input-area {
  display: flex;
  align-items: center;
  padding: 10px;
  background-color: #fff;
  border-top: 1px solid #e0e0e0;
}

.input-tools {
  display: flex;
  margin-right: 10px;
}

.input-container {
  flex: 1;
  margin-right: 10px;
}

.input-container input {
  width: 100%;
  padding: 10px 15px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  outline: none;
}

.send-button-container {
  margin-left: 10px;
}

.send-btn {
  background-color: #07c160;
  color: #fff;
  border: none;
  padding: 10px 20px;
  border-radius: 20px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.send-btn:hover {
  background-color: #06b355;
}
</style>