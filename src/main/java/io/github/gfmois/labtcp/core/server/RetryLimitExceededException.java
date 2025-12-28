package io.github.gfmois.labtcp.core.server;

public class RetryLimitExceededException extends TcpLabException {
	private final int maxRetries;

	public RetryLimitExceededException(int maxRetries, Throwable cause) {
		super("Retry limit exceeded: " + maxRetries, cause);
		this.maxRetries = maxRetries;
	}

	public int getMaxRetries() {
        return maxRetries;
    }
}
