import { describe, expect, it, vi } from 'vitest';
import { renderEChartsInContainer } from './useEchartsRenderer';

vi.mock('echarts/core', () => ({
	use: vi.fn(),
	getInstanceByDom: vi.fn((element: any) => element.instance),
	init: vi.fn((element: any) => {
		element.canvas = { attached: true };
		element.instance = { setOption: vi.fn() };
		return element.instance;
	}),
}));

describe('ECharts result updates', () => {
	it('keeps the existing canvas attached when a task result is refreshed', () => {
		let config: string | null = JSON.stringify({ series: [{ data: [1] }] });
		const element: any = {
			canvas: null,
			getAttribute: () => config,
			removeAttribute: () => {
				config = null;
			},
			set textContent(value: string) {
				this.canvas = null;
			},
		};
		const container = {
			querySelectorAll: () => [element],
		} as unknown as HTMLElement;
		renderEChartsInContainer(container);
		const originalCanvas = element.canvas;
		config = JSON.stringify({ series: [{ data: [2] }] });
		renderEChartsInContainer(container);
		expect(originalCanvas).toBeTruthy();
		expect(element.canvas).toBe(originalCanvas);
		expect(element.instance.setOption).toHaveBeenCalledTimes(2);
	});
});
