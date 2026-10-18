// Keyboard-first: shortcuts work anywhere on the page, but never while you're typing in a field.
document.addEventListener('keydown', (event) => {
    const typing = event.target.closest('input, textarea, select, [contenteditable]');

    if (event.key === 'Escape') {
        const cancel = document.getElementById('cancel-edit');
        if (cancel) {
            cancel.click();
        } else if (typing) {
            typing.blur();
        }
        return;
    }
    if (typing || event.ctrlKey || event.metaKey || event.altKey) {
        return;
    }

    const click = (id) => document.getElementById(id)?.click();
    if (event.key === '[') {
        click('prev-week');
    } else if (event.key === ']') {
        click('next-week');
    } else if (event.key === 'n') {
        const task = document.getElementById('qa-desc');
        if (task) {
            event.preventDefault();
            task.focus();
        }
    }
});

// <form data-confirm="..."> asks before submitting.
document.addEventListener('submit', (event) => {
    const message = event.target.dataset.confirm;
    if (message && !window.confirm(message)) {
        event.preventDefault();
    }
});

// Browsers skip autofocus on some navigations (back/forward, restored tabs); make it dependable.
document.addEventListener('DOMContentLoaded', () => {
    document.querySelector('[autofocus]')?.focus();
});

// Running timer: tick the header chip every few seconds; "t" stops it, or starts one from the entry form.
(() => {
    const elapsed = document.getElementById('timer-elapsed');
    if (elapsed) {
        const started = Number(elapsed.dataset.started);
        const tick = () => {
            const minutes = Math.max(0, Math.floor((Date.now() - started) / 60000));
            elapsed.textContent = Math.floor(minutes / 60) + ':' + String(minutes % 60).padStart(2, '0');
        };
        tick();
        setInterval(tick, 5000);
    }
    document.addEventListener('keydown', (event) => {
        if (event.key !== 't' || event.target.closest('input, textarea, select, [contenteditable]')
                || event.ctrlKey || event.metaKey || event.altKey) {
            return;
        }
        const stop = document.getElementById('timer-stop');
        const start = document.getElementById('timer-start');
        if (stop) {
            stop.requestSubmit();
        } else if (start) {
            start.click();
        }
    });
})();

// Entry bar: timer / manual switch, remembered in this browser (a convenience,
// so localStorage is fine). While a timer runs, the bar always opens in timer mode.
(() => {
    const bar = document.getElementById('quick-add');
    if (!bar) {
        return;
    }
    const modes = bar.querySelector('.eb-modes');
    const setMode = (mode, remember) => {
        bar.dataset.mode = mode;
        modes.querySelectorAll('button').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.mode === mode)));
        if (remember) {
            try { localStorage.setItem('entryMode', mode); } catch { /* private window: just don't remember */ }
        }
    };
    let saved = null;
    try { saved = localStorage.getItem('entryMode'); } catch { /* ignore */ }
    modes.hidden = false;
    setMode(bar.dataset.running === 'true' || saved === 'timer' ? 'timer' : 'manual', false);
    modes.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-mode]');
        if (button) {
            setMode(button.dataset.mode, true);
            document.getElementById('qa-desc')?.focus();
        }
    });

    const clock = document.getElementById('bar-clock');
    if (clock?.dataset.started) {
        const started = Number(clock.dataset.started);
        const tick = () => {
            const s = Math.max(0, Math.floor((Date.now() - started) / 1000));
            clock.textContent = [Math.floor(s / 3600), Math.floor(s / 60) % 60, s % 60]
                .map((n) => String(n).padStart(2, '0')).join(':');
        };
        tick();
        setInterval(tick, 1000);
    }
})();
