<template>
	<v-app><slot /></v-app>
</template>
<style>
html,
body {
	background: #f5f6f8;
}
body {
	color: #19263c;
	font-family: Inter, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
* {
	box-sizing: border-box;
}
button,
input,
textarea,
select {
	font: inherit;
}
button,
a,
select {
	-webkit-tap-highlight-color: transparent;
}
button:focus-visible,
a:focus-visible,
input:focus-visible,
textarea:focus-visible,
select:focus-visible {
	outline: 3px solid #83a9ff;
	outline-offset: 3px;
}
</style>
