export interface CommerceUser {
	id: number;
	username: string;
	role: 'ADMIN' | 'ANALYST';
	shopId: number | null;
}

export async function commerceRequest<T>(
	url: string,
	options: Record<string, any> = {},
): Promise<T> {
	const headers: Record<string, string> = { ...options.headers };
	if (options.method && options.method !== 'GET') {
		const csrf = await $fetch<{ token: string; headerName: string }>(
			'/api/auth/csrf',
		);
		headers[csrf.headerName] = csrf.token;
	}
	return $fetch<T>(url, { ...options, headers });
}

export function commerceError(error: any): string {
	return (
		error?.data?.message ||
		error?.data?.detail ||
		error?.message ||
		'请求失败，请检查服务状态'
	);
}
