import { afterEach, describe, expect, it, vi } from 'vitest';
import { commerceRequest, commerceError } from './useCommerceApi';

afterEach(() => vi.unstubAllGlobals());
describe('Commerce API session and CSRF requests', () => {
	it('fetches CSRF before state-changing requests and keeps the idempotency key', async () => {
		const fetch = vi
			.fn()
			.mockResolvedValueOnce({ token: 'test-csrf', headerName: 'X-CSRF-TOKEN' })
			.mockResolvedValueOnce({ id: 'task' });
		vi.stubGlobal('$fetch', fetch);
		const response = await commerceRequest('/api/analysis/tasks', {
			method: 'POST',
			headers: { 'Idempotency-Key': 'request-1' },
		});
		expect(response).toEqual({ id: 'task' });
		expect(fetch.mock.calls[0][0]).toBe('/api/auth/csrf');
		expect(fetch.mock.calls[1][1].headers).toEqual({
			'Idempotency-Key': 'request-1',
			'X-CSRF-TOKEN': 'test-csrf',
		});
	});
	it('uses a single request for reads', async () => {
		const fetch = vi.fn().mockResolvedValue([]);
		vi.stubGlobal('$fetch', fetch);
		await commerceRequest('/api/analysis/tasks');
		expect(fetch).toHaveBeenCalledTimes(1);
	});
	it('preserves server failure messages', () => {
		expect(commerceError({ data: { message: '没有该店铺的数据权限' } })).toBe(
			'没有该店铺的数据权限',
		);
		expect(commerceError(null)).toBe('请求失败，请检查服务状态');
	});
});
