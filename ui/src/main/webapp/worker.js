Error.stackTraceLimit = 50;
(async function() {
    let teavmSupport = await import('./compiler.wasm-runtime.js');
    let teavm = await teavmSupport.load("compiler.wasm", {
        stackDeobfuscator: {
            enabled: true
        }
    });

    teavm.exports.installWorker();
})();
