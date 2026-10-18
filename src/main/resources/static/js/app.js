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
        const task = document.getElementById('qa-task');
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
