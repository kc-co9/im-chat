// API配置文件
// 根据环境变量获取不同的API地址

// 默认API地址（开发环境）
const DEFAULT_API_BASE_URL = window.location.protocol + '//' + window.location.hostname + ':8888';

// 从环境变量获取API地址（生产环境可能会设置）
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL;

export const API_CONFIG = {
  // API基础地址
  baseUrl: API_BASE_URL,
  
  // 用户相关API
  user: {
    signIn: `${API_BASE_URL}/user/signIn`,
    signUp: `${API_BASE_URL}/user/signUp`,
    signOut: `${API_BASE_URL}/user/signOut`,
    userDetail: `${API_BASE_URL}/user/userDetail`
  },
  
  // 聊天相关API
  chat: {
    getChatList: `${API_BASE_URL}/im/chat/getChatList`,
    enterPrivateChat: `${API_BASE_URL}/im/chat/enterPrivateChat`,
    markAsRead: `${API_BASE_URL}/im/chat/markAsRead`
  },
  
  // 好友相关API
  friend: {
    friendList: `${API_BASE_URL}/friend/friendList`
  },
  
  // 消息相关API
  message: {
    queryHistoryMessage: `${API_BASE_URL}/im/private/queryHistoryMessage`
  },
  
  // WebSocket相关配置
  ws: {
    baseUrl: `${API_BASE_URL}/ws`,
    web: `${API_BASE_URL}/ws/web`,
    endpoints: {
      privateMessage: '/message/private/send',
      queueResult: '/queue/result'
    }
  }
};
