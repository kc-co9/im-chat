<template>
  <div class="register-container">
    <div class="register-box">
      <h2>创建账号</h2>
      <form @submit.prevent="register">
        <div class="form-group">
          <label for="username">用户名</label>
          <input type="text" id="username" v-model="form.username" placeholder="请输入用户名" required>
        </div>
        <div class="form-group">
          <label for="email">邮箱</label>
          <input type="email" id="email" v-model="form.email" placeholder="请输入邮箱" required>
        </div>
        <div class="form-group">
          <label for="password">密码</label>
          <input type="password" id="password" v-model="form.password" placeholder="请输入密码" required>
        </div>
        <div class="form-group">
          <label for="confirmPassword">确认密码</label>
          <input type="password" id="confirmPassword" v-model="form.confirmPassword" placeholder="请再次输入密码" required>
        </div>
        <div class="form-options">
          <label>
            <input type="checkbox" id="agree-terms" v-model="form.agreeTerms" required> 我已阅读并同意<a href="#" class="terms-link">服务条款</a>和<a href="#" class="privacy-link">隐私政策</a>
          </label>
        </div>
        <button type="submit" class="register-btn" :disabled="loading">
          <i v-if="loading" class="fa fa-spinner fa-spin"></i>
          {{ loading ? '注册中...' : '注册' }}
        </button>
        <div class="login-link">
          <p>已有账号？<router-link to="/login">立即登录</router-link></p>
        </div>
      </form>
    </div>
  </div>
</template>

<script>
import { API_CONFIG } from '../config/api.js';

export default {
  name: 'Register',
  data() {
    return {
      form: {
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
        agreeTerms: false
      },
      loading: false
    }
  },
  methods: {
    register() {
      // 表单验证
      if (this.form.password !== this.form.confirmPassword) {
        alert('两次输入的密码不一致');
        return;
      }
      
      if (this.form.password.length < 6) {
        alert('密码长度不能少于6个字符');
        return;
      }
      
      this.loading = true;
      
      // 发送注册请求
      fetch(API_CONFIG.user.signUp, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          username: this.form.username,
          email: this.form.email,
          password: this.form.password
        })
      })
      .then(response => response.json())
      .then(response => {
        this.loading = false;
        if (response.code === 0) {
          // 注册成功，跳转到登录页面
          alert('注册成功，请登录');
          this.$router.push('/login');
        } else {
          // 注册失败，显示错误信息
          alert('注册失败：' + (response.msg || '未知错误'));
        }
      })
      .catch(error => {
        this.loading = false;
        // 请求失败，显示错误信息
        alert('注册失败：' + error);
      });
    }
  }
}
</script>

<style scoped>
/* 注册页面样式 */
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f5f5f5;
}

.register-box {
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  padding: 30px;
  width: 100%;
  max-width: 400px;
}

.register-box h2 {
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
  margin-bottom: 20px;
  font-size: 14px;
}

.form-options a {
  color: #07c160;
  text-decoration: none;
}

.register-btn {
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

.register-btn:hover {
  background-color: #06b355;
}

.register-btn:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.login-link {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
}

.login-link a {
  color: #07c160;
  text-decoration: none;
}
</style>