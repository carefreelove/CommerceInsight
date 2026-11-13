<template>
	<main class="login-page">
		<section class="intro">
			<NuxtLink to="/commerce" class="brand"
				>Commerce<span>Insight</span></NuxtLink
			>
			<div class="intro-copy">
				<p class="eyebrow">电商经营分析平台</p>
				<h1>让每次分析<br />都有数据依据。</h1>
				<p class="description">
					从经营问题到 SQL、结果和报告，<br />在一个工作台中追踪完整分析过程。
				</p>
			</div>
			<p class="source">基于 Spring AI Alibaba DataAgent 二次开发</p>
		</section>
		<section class="form-side">
			<form class="login-card" @submit.prevent="login">
				<p class="eyebrow">欢迎回来</p>
				<h2>登录分析工作台</h2>
				<p class="muted">使用你的账号访问已授权店铺的数据</p>
				<label for="username">用户名</label
				><input
					id="username"
					v-model="username"
					autocomplete="username"
					required
					maxlength="60"
				/>
				<label for="password">密码</label
				><input
					id="password"
					v-model="password"
					type="password"
					autocomplete="current-password"
					required
					maxlength="200"
				/>
				<p v-if="error" class="error" role="alert">{{ error }}</p>
				<button class="submit" :disabled="busy">
					{{ busy ? '正在登录…' : '进入工作台 →' }}
				</button>
				<div class="demo">
					<strong>本地模拟数据演示</strong>
					<p>
						管理员账号 admin，分析员账号 analyst1 / analyst2。初始密码由
						CI_DEMO_PASSWORD 配置，默认 CommerceDemo!2026。
					</p>
				</div>
			</form>
		</section>
	</main>
</template>
<script setup lang="ts">
import { commerceRequest, commerceError } from '~/composables/useCommerceApi';
definePageMeta({ layout: 'commerce' });
useHead({ title: '登录 · CommerceInsight' });
const username = ref('analyst1');
const password = ref('');
const error = ref('');
const busy = ref(false);
async function login() {
	busy.value = true;
	error.value = '';
	try {
		await commerceRequest('/api/auth/login', {
			method: 'POST',
			body: { username: username.value, password: password.value },
		});
		await navigateTo('/commerce');
	} catch (e) {
		error.value = commerceError(e);
	} finally {
		busy.value = false;
	}
}
</script>
<style scoped>
.login-page {
	min-height: 100vh;
	display: grid;
	grid-template-columns: 1fr 1fr;
}
.intro {
	padding: 50px 64px;
	background: #12233e;
	color: white;
	display: flex;
	flex-direction: column;
	justify-content: space-between;
}
.brand {
	font-weight: 750;
	font-size: 25px;
	text-decoration: none;
	color: white;
	letter-spacing: -1px;
}
.brand span {
	color: #91b8ff;
}
.intro-copy {
	padding: 70px 0;
}
.eyebrow {
	font-size: 13px;
	letter-spacing: 2px;
	margin-bottom: 18px;
	color: #869bb7;
}
.intro h1 {
	font-size: clamp(38px, 4.3vw, 66px);
	line-height: 1.35;
	letter-spacing: -2px;
	font-weight: 600;
}
.description {
	font-size: 16px;
	color: #b1bfd3;
	line-height: 1.9;
	margin-top: 30px;
}
.source {
	font-size: 12px;
	color: #7e91ac;
}
.form-side {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 48px;
}
.login-card {
	width: 100%;
	max-width: 370px;
}
.login-card h2 {
	font-size: 29px;
	margin-bottom: 10px;
}
.muted {
	color: #778499;
	font-size: 14px;
	margin-bottom: 32px;
}
label {
	display: block;
	font-size: 14px;
	font-weight: 600;
	margin: 20px 0 8px;
}
input {
	width: 100%;
	border: 1px solid #d8dfe9;
	border-radius: 7px;
	padding: 12px;
	background: white;
}
.submit {
	width: 100%;
	background: #3267d6;
	color: white;
	border-radius: 7px;
	padding: 13px;
	margin-top: 28px;
	font-weight: 600;
	cursor: pointer;
}
.submit:disabled {
	opacity: 0.6;
}
.error {
	color: #b64040;
	margin-top: 16px;
}
.demo {
	margin-top: 30px;
	border-top: 1px solid #dce2eb;
	padding-top: 20px;
	font-size: 12px;
	color: #718097;
	line-height: 1.8;
}
.demo strong {
	color: #4d5d75;
}
.demo p {
	margin-top: 6px;
}
@media (max-width: 760px) {
	.login-page {
		grid-template-columns: 1fr;
	}
	.intro {
		padding: 28px;
	}
	.intro-copy {
		padding: 38px 0;
	}
	.intro h1 {
		font-size: 34px;
	}
	.description,
	.source {
		display: none;
	}
	.form-side {
		padding: 32px;
	}
}
</style>
