package com.dianping.phoenix.deploy.agent;

import com.dianping.phoenix.agent.response.entity.Response;
import com.dianping.phoenix.agent.response.transform.DefaultJsonParser;

public enum State {
	CREATED(0, 1, 2, 9) {
		@Override
		protected void doActivity(Context ctx) throws Exception {
			int id = ctx.getDeployId();
			String domain = ctx.getDomain();
			String version = ctx.getVersion();
			String host = ctx.getHost();
			String url = ctx.getConfigManager().getDeployUrl(host, id, domain, version);
			String json = null;

			ctx.println(String.format("[INFO] Deploy URL: %s", url));
			ctx.print("[INFO] Deploying phoenix kernel(%s) to host(%s) for deploy(%s) of domain(%s)  ... ", version, host,
			      id, domain);

			try {
				json = ctx.openUrl(url);
			} catch (Exception e) {
				ctx.println(e.toString());
				moveTo(ctx, UNREACHABLE);
				return;
			}

			Response response;

			try {
				response = DefaultJsonParser.parse(json);
			} catch (Exception e) {
				ctx.println("Invalid JSON result:\r\n%s", json);
				return;
			}

			if ("ok".equals(response.getStatus())) {
				ctx.println("ACCEPTED");

				moveTo(ctx, SUBMITTED);
			} else {
				ctx.print(response.getStatus()).println();
				ctx.println(response.getMessage());
				moveTo(ctx, FAILED);
			}
		}
	},

	UNREACHABLE(1, 1, 2, 9) {
		private static final int MAX_RETRY_COUNT = 2;

		@Override
		protected void doActivity(Context ctx) throws Exception {
			int retriedCount = ctx.getRetriedCount();

			if (retriedCount >= MAX_RETRY_COUNT) {
				moveTo(ctx, FAILED);
			} else {
				long retryInterval = ctx.getConfigManager().getDeployRetryInterval();

				ctx.setRetriedCount(retriedCount + 1);

				Thread.sleep(retryInterval); // sleep a while before retry

				String host = ctx.getHost();
				int id = ctx.getDeployId();
				String domain = ctx.getDomain();
				String version = ctx.getVersion();
				String url = ctx.getConfigManager().getDeployUrl(host, id, domain, version);
				String json = null;

				ctx.print("[WARN] Retry to deploy phoenix kernel(%s) to host(%s) for deploy(%s) of domain(%s)  ... ",
				      version, host, id, domain);

				try {
					json = ctx.openUrl(url);
				} catch (Exception e) {
					ctx.println(e.toString());
					moveTo(ctx, UNREACHABLE);
					return;
				}

				Response response = DefaultJsonParser.parse(json);

				if ("ok".equals(response.getStatus())) {
					ctx.println("ACCEPTED");

					moveTo(ctx, SUBMITTED);
				} else {
					ctx.print(response.getStatus()).println();
					ctx.println(response.getMessage());
					moveTo(ctx, FAILED);
				}
			}
		}
	},

	SUBMITTED(2, 4, 9) {
		@Override
		protected void doActivity(Context ctx) throws Exception {
			String host = ctx.getHost();
			int id = ctx.getDeployId();

			ctx.println("[INFO] Getting status from host(%s) for deploy(%s) ... ", host, id);

			String url = ctx.getConfigManager().getDeployLogUrl(host, id);

			try {
				String log = ctx.openUrl(url);

				ctx.print(log);
			} catch (Exception e) {
				ctx.println(e.toString());
				moveTo(ctx, FAILED);
				return;
			}

			if (ctx.isFailed()) {
				moveTo(ctx, FAILED);
			} else {
				moveTo(ctx, SUCCESSFUL);
			}
		}
	},

	SUCCESSFUL(4) {
		@Override
		protected void doActivity(Context ctx) throws Exception {
			String version = ctx.getVersion();
			String host = ctx.getHost();

			ctx.println("[INFO] Deployed phoenix kernel(%s) to host(%s) successfully.", version, host);
		}
	},

	FAILED(9) {
		@Override
		protected void doActivity(Context ctx) throws Exception {
			String version = ctx.getVersion();
			String host = ctx.getHost();
			String message = String.format("[ERROR] Failed to deploy phoenix kernel(%s) to host(%s).", version, host);

			ctx.updateStatus("failed", message);
			ctx.println(message);
		}
	};

	private int m_id;

	private int[] m_nextIds;

	private State(int id, int... nextIds) {
		m_id = id;
		m_nextIds = nextIds;
	}

	public static void execute(Context ctx) throws Exception {
		State initial = CREATED;

		ctx.setState(initial);
		initial.doActivity(ctx);
	}

	protected abstract void doActivity(Context ctx) throws Exception;

	public int getId() {
		return m_id;
	}

	void moveTo(Context ctx, State nextState) throws Exception {
		int nextId = nextState.getId();
		boolean found = false;

		for (int id : m_nextIds) {
			if (id == nextId) {
				found = true;
				break;
			}
		}

		if (!found) {
			throw new IllegalStateException(String.format("Can't move deploy state from %s to %s!", this, nextState));
		} else {
			ctx.setState(nextState);
		}

		nextState.doActivity(ctx);
	}
}
