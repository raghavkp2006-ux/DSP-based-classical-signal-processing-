# EQ default audit

The `v1.0.0` tag is `f68e100`. It predates `PipelineCore.kt`: production `RunPipeline.run` directly invokes `Eq.apply`, the causal legacy EQ. On `fix/dsp-fidelity`, `RunPipeline.run` calls `PipelineCore.process`, which passes `legacyEq=false` and selects `Eq.applyAligned`: the current production Kotlin arm is **Kotlin aligned EQ**. This is a pre-existing difference from the tagged release; this audit changes no defaults.

Current selection points:

- Kotlin `RunPipeline.run` calls `PipelineCore.process`; `process` passes `false` to the internal `legacyEq` selector; `processInternal` chooses `Eq.applyAligned` when false and `Eq.apply` when true. `processForEvaluation` defaults `legacyEq=false` (aligned); the ablation exporter explicitly passes true for its legacy arm. `Eq.apply` and `Eq.applyAligned` are explicit separate functions, each with gain default 1.0, rather than a mode selector.
- Python `run_pipeline` defaults `aligned_eq=False` and selects `apply_eq` (legacy); `aligned_eq=True` selects `apply_eq_aligned`. The aligned evaluation explicitly opts in. `compare_pipeline_stages.py`, `train_denoiser.py`, and `scripts/make_eq_golden.py` explicitly call legacy `apply_eq`. Other `run_pipeline` callers that omit the option use legacy. Python feedback retry recursively calls `run_pipeline` without forwarding `aligned_eq`; therefore a retry uses legacy even if the initial call requested aligned. The controlled evaluations disable feedback; no change was made to retry behavior.
- Both agents may set `use_eq=false` / `useEq=false`, notably in Light touch, in which case either EQ choice is bypassed. Default non-agent parameters enable EQ with gain 1.0; agent decisions choose gain and whether it is active.

All subsequent evaluation tables identify actual EQ arm and mark Kotlin aligned as the current production app configuration; this is not the v1.0.0 EQ configuration.
