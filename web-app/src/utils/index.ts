// biome-ignore lint/suspicious/noEmptyBlockStatements: Intentional no-op callback for read-only inputs.
export const NO_OP = () => {};

export const isJson = (maybeJson: string): boolean => {
	try {
		JSON.parse(maybeJson);
		return true;
	} catch (e) {
		return false;
	}
};
