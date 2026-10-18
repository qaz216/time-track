// Project picker: turns <select data-picker> into a searchable popover grouped by
// client, with projects that expand to their tasks (Clockify-style).
//
// The <select> stays in the form and holds the value, so the form submits
// exactly as before and still works if this script never runs. Everything the
// picker shows is read from the select's options:
//   <optgroup label="CTG">                      -> heading
//   <option data-project-id data-project data-colour> -> the project itself (its default task)
//   <option data-project-id data-project data-task="Design"> -> a named task
(() => {
    // each option carries its project's colour (data-colour), computed once on the server
    const colours = new Map();
    const colour = (projectId) => colours.get(projectId) ?? '#999';

    const el = (tag, attrs = {}, ...children) => {
        const node = document.createElement(tag);
        for (const [key, value] of Object.entries(attrs)) {
            if (value !== null && value !== undefined) node.setAttribute(key, value);
        }
        node.append(...children);
        return node;
    };

    const plusIcon = () => {
        const span = el('span', {class: 'picker-plus', 'aria-hidden': 'true'});
        span.innerHTML = '<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="6.5"/><path d="M8 5v6M5 8h6"/></svg>';
        return span;
    };

    function readGroups(select) {
        return [...select.querySelectorAll('optgroup')].map((group) => {
            const projects = new Map();
            for (const option of group.querySelectorAll('option')) {
                const id = Number(option.dataset.projectId);
                colours.set(id, option.dataset.colour);
                if (!projects.has(id)) {
                    projects.set(id, {id, name: option.dataset.project, value: null, tasks: []});
                }
                const project = projects.get(id);
                if (option.dataset.task) {
                    project.tasks.push({value: option.value, name: option.dataset.task});
                } else {
                    project.value = option.value;
                }
            }
            return {client: group.label, projects: [...projects.values()]};
        });
    }

    function enhance(select) {
        const groups = readGroups(select);
        const form = select.form;
        const wrap = select.parentElement;
        wrap.classList.add('picker');
        select.hidden = true;
        select.tabIndex = -1;

        const button = el('button', {type: 'button', class: 'picker-button', 'aria-haspopup': 'listbox',
            'aria-expanded': 'false', title: 'Project'});
        const search = el('input', {type: 'search', class: 'picker-search', placeholder: 'Search project or task',
            'aria-label': 'Search projects', autocomplete: 'off', role: 'combobox', 'aria-expanded': 'true',
            'aria-controls': `${select.id}-list`});
        const hint = el('p', {class: 'picker-hint', hidden: ''}, 'Pick a project first.');
        const list = el('ul', {class: 'picker-list', role: 'listbox', id: `${select.id}-list`});
        const pop = el('div', {class: 'picker-pop', hidden: ''}, search, hint, list);
        wrap.append(button, pop);

        const expanded = new Set();   // project ids showing their tasks
        const collapsed = new Set();  // client headings folded away
        let options = [];             // the selectable <li>s currently shown
        let active = -1;

        function selectedOption() {
            return select.selectedOptions[0]?.value ? select.selectedOptions[0] : null;
        }

        function renderButton() {
            const option = selectedOption();
            button.replaceChildren();
            if (option) {
                button.append(el('span', {class: 'dot', style: `background:${colour(Number(option.dataset.projectId))}`}),
                    el('span', {class: 'picker-label'}, option.textContent));
                button.classList.add('chosen');
            } else {
                button.append(plusIcon(), el('span', {class: 'picker-label'}, 'Project'));
                button.classList.remove('chosen');
            }
        }

        function optionRow(value, label, extraClass, projectId) {
            const li = el('li', {role: 'option', id: `${select.id}-opt-${value}`, class: `picker-option ${extraClass}`,
                'data-value': value, 'aria-selected': String(select.value === value)},
            el('span', {class: 'dot', style: `background:${colour(projectId)}`}), el('span', {class: 'name'}, label));
            options.push(li);
            return li;
        }

        function render() {
            const query = search.value.trim().toLowerCase();
            const matches = (text) => text.toLowerCase().includes(query);
            options = [];
            list.replaceChildren();

            for (const group of groups) {
                const shown = group.projects
                    .map((project) => {
                        const projectHit = !query || matches(project.name) || matches(group.client);
                        const tasks = project.tasks.filter((t) => projectHit || matches(t.name));
                        return (projectHit || tasks.length) ? {project, tasks, projectHit} : null;
                    })
                    .filter(Boolean);
                if (!shown.length) continue;

                const folded = collapsed.has(group.client) && !query;
                const heading = el('li', {class: 'picker-heading', role: 'presentation'},
                    el('span', {}, group.client),
                    el('span', {class: 'count'}, `${shown.length} ${shown.length === 1 ? 'Project' : 'Projects'} ${folded ? '⌄' : '⌃'}`));
                heading.addEventListener('mousedown', (event) => {
                    event.preventDefault();
                    folded ? collapsed.delete(group.client) : collapsed.add(group.client);
                    render();
                });
                list.append(heading);
                if (folded) continue;

                for (const {project, tasks, projectHit} of shown) {
                    const open = expanded.has(project.id) || (query && !projectHit) || (query && tasks.length && project.value === null);
                    let row;
                    if (project.value !== null) {
                        row = optionRow(project.value, project.name, 'project', project.id);
                    } else {
                        row = el('li', {class: 'picker-option project no-default', role: 'presentation'},
                            el('span', {class: 'dot', style: `background:${colour(project.id)}`}),
                            el('span', {class: 'name'}, project.name));
                    }
                    if (project.tasks.length) {
                        const toggle = el('button', {type: 'button', class: 'tasks-toggle', tabindex: '-1'},
                            `${project.tasks.length} ${project.tasks.length === 1 ? 'Task' : 'Tasks'} ${open ? '⌃' : '⌄'}`);
                        const flip = (event) => {
                            event.preventDefault();
                            event.stopPropagation();
                            open ? expanded.delete(project.id) : expanded.add(project.id);
                            render();
                        };
                        toggle.addEventListener('mousedown', flip);
                        if (project.value === null) row.addEventListener('mousedown', flip);
                        row.append(toggle);
                    }
                    list.append(row);
                    if (open) {
                        for (const task of tasks) {
                            list.append(optionRow(task.value, task.name, 'task', project.id));
                        }
                    }
                }
            }
            if (!options.length) {
                list.append(el('li', {class: 'picker-empty', role: 'presentation'}, 'No project matches.'));
            }
            highlight(Math.min(Math.max(active, 0), options.length - 1));
        }

        function highlight(index) {
            options.forEach((li) => li.classList.remove('active'));
            active = index;
            const li = options[index];
            if (li) {
                li.classList.add('active');
                li.scrollIntoView({block: 'nearest'});
                search.setAttribute('aria-activedescendant', li.id);
            } else {
                search.removeAttribute('aria-activedescendant');
            }
        }

        function choose(value) {
            select.value = value;
            select.dispatchEvent(new Event('change', {bubbles: true}));
            renderButton();
            close();
        }

        function open(initialQuery = '') {
            pop.hidden = false;
            button.setAttribute('aria-expanded', 'true');
            search.value = initialQuery;
            active = Math.max(0, options.findIndex((li) => li.dataset.value === select.value));
            render();
            search.focus();
        }

        function close(refocus = true) {
            if (pop.hidden) return;
            pop.hidden = true;
            hint.hidden = true;
            button.setAttribute('aria-expanded', 'false');
            if (refocus) button.focus();
        }

        button.addEventListener('click', () => (pop.hidden ? open() : close()));
        button.addEventListener('keydown', (event) => {
            if (['ArrowDown', 'Enter', ' '].includes(event.key)) {
                event.preventDefault();
                open();
            } else if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
                event.preventDefault();       // start typing to search straight away
                open(event.key);
            }
        });
        search.addEventListener('input', () => {
            active = 0;
            render();
        });
        search.addEventListener('keydown', (event) => {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                const step = event.key === 'ArrowDown' ? 1 : -1;
                highlight((active + step + options.length) % options.length);
            } else if (event.key === 'Enter') {
                event.preventDefault();
                if (options[active]) choose(options[active].dataset.value);
            } else if (event.key === 'Escape') {
                event.preventDefault();
                event.stopPropagation();      // don't let the page-level Esc cancel an edit too
                close();
            } else if (event.key === 'Tab') {
                close(false);
            }
        });
        list.addEventListener('mousedown', (event) => {
            const li = event.target.closest('li[role="option"]');
            if (li) {
                event.preventDefault();
                choose(li.dataset.value);
            }
        });
        document.addEventListener('mousedown', (event) => {
            if (!wrap.contains(event.target)) close(false);
        });

        // Adding needs a project; stopping a timer doesn't.
        form?.addEventListener('submit', (event) => {
            const action = event.submitter?.getAttribute('formaction') || '';
            if (!select.value && !action.endsWith('/timer/stop')) {
                event.preventDefault();
                event.stopImmediatePropagation();
                open();
                hint.hidden = false;
            }
        }, true);

        renderButton();
    }

    document.querySelectorAll('select[data-picker]').forEach(enhance);
})();
