from sandbox import metrics


def test_metrics_inc_and_observe():
    before = metrics.snapshot()
    metrics.inc("launch_success")
    metrics.observe_launch_ms(12.5)
    after = metrics.snapshot()
    assert after["launch_success"] == before["launch_success"] + 1
    assert after["launch_samples"] >= 1
    assert after["launch_latency_ms_p95"] >= 0
