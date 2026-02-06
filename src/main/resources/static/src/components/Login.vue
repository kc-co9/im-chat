<template>
  <div class="login-container">
    <div class="login-box">
      <h2>即时通讯应用</h2>
      <form @submit.prevent="login">
        <div class="form-group">
          <label for="email">邮箱</label>
          <input type="email" id="email" v-model="form.email" placeholder="请输入邮箱" required>
        </div>
        <div class="form-group">
          <label for="password">密码</label>
          <input type="password" id="password" v-model="form.password" placeholder="请输入密码" required>
        </div>
        <div class="form-options">
          <label>
            <input type="checkbox" id="remember-me" v-model="form.rememberMe"> 记住我
          </label>
          <a href="#" class="forgot-password">忘记密码？</a>
        </div>
        <button type="submit" class="login-btn" :disabled="loading">
          <i v-if="loading" class="fa fa-spinner fa-spin"></i>
          {{ loading ? '登录中...' : '登录' }}
        </button>
        <div class="register-link">
          <p>还没有账号？<router-link to="/register">立即注册</router-link></p>
        </div>
      </form>
    </div>
  </div>
</template>

<script>
import { API_CONFIG } from '../config/api.js';

export default {
  name: 'Login',
  data() {
    return {
      form: {
        email: '',
        password: '',
        rememberMe: false
      },
      loading: false
    }
  },
  methods: {
    login() {
      this.loading = true;
      
      // 发送登录请求
      fetch(API_CONFIG.user.signIn, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          email: this.form.email,
          password: this.form.password
        })
      })
      .then(response => response.json())
      .then(response => {
        this.loading = false;
        console.log('登录响应:', JSON.stringify(response, null, 2));
        
        if (response.code === 0 && response.data && response.data.token) {
          // 登录成功，获取token
          const token = response.data.token;
          console.log('登录成功，获取到token:', token);
          console.log('token类型:', typeof token);
          console.log('token长度:', token.length);
          
          // 验证token格式
          const tokenParts = token.split('.');
          console.log('token格式验证:', tokenParts.length === 3 ? '通过' : '失败');
          
          // 跳转到聊天页面，使用路由参数传递token
          console.log('准备跳转到聊天页面');
          
          // 对于hash路由，使用name或path+query的方式
          this.$router.push({
            path: '/chat',
            query: { token: token }
          });
        } else if (response.code !== 0) {
          // 登录失败，显示错误信息
          alert('登录失败：' + (response.msg || '未知错误'));
        } else {
          // 其他错误情况
          alert('登录失败：无法获取token');
          console.error('登录响应:', response);
        }
      })
      .catch(error => {
        this.loading = false;
        // 请求失败，显示错误信息
        alert('登录失败：' + error);
      });
    }
  }
}
</script>

<style scoped>
/* 登录页面样式 */
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f5f5f5;
}

.login-box {
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  padding: 30px;
  width: 100%;
  max-width: 400px;
}

.login-box h2 {
  text-align: center;
  margin-bottom: 20px;
  color: #333;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
  color: #666;
}

.form-group input {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 16px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  font-size: 14px;
}

.forgot-password {
  color: #07c160;
  text-decoration: none;
}

.login-btn {
  width: 100%;
  padding: 12px;
  background-color: #07c160;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.login-btn:hover {
  background-color: #06b355;
}

.login-btn:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.register-link {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
}

.register-link a {
  color: #07c160;
  text-decoration: none;
}
</style>