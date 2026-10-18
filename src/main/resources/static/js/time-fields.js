// Start / end / duration boxes, Clockify-style: change one and the others follow,
// and typed times tidy themselves on blur ("115pm" -> "1:15 PM").
//
// This is only a preview. The server parses and checks everything again
// (ClockTime, TimeFields, TimeInput), so anything this can't read is left as
// typed and the server explains what's wrong.
(() => {
    const DAY = 24 * 3600;

    // Mirrors ClockTime.parse: 9, 915, 1315, 1:15pm, 13:15:30, 12am ... -> seconds of the day
    function parseClock(raw) {
        let text = raw.trim().toLowerCase().replace(/[\s.]/g, '');
        if (!text) return null;
        let pm = null;
        const suffix = text.match(/(am|pm|a|p)$/);
        if (suffix) {
            pm = suffix[1].startsWith('p');
            text = text.slice(0, -suffix[1].length);
        }
        let h, m = 0, s = 0, match;
        if ((match = text.match(/^(\d{1,2}):(\d{2})(?::(\d{2}))?$/))) {
            h = +match[1]; m = +match[2]; s = +(match[3] ?? 0);
        } else if (/^\d{1,6}$/.test(text)) {
            const hourDigits = text.length <= 2 ? text.length : (text.length % 2 === 1 ? 1 : 2);
            h = +text.slice(0, hourDigits);
            const rest = text.slice(hourDigits);
            m = rest.length >= 2 ? +rest.slice(0, 2) : 0;
            s = rest.length >= 4 ? +rest.slice(2, 4) : 0;
        } else {
            return null;
        }
        if (m > 59 || s > 59) return null;
        if (pm !== null) {
            if (h < 1 || h > 12) return null;
            h = h % 12 + (pm ? 12 : 0);
        } else if (h > 24 || (h === 24 && (m || s))) {
            return null;
        }
        return h * 3600 + m * 60 + s;
    }

    // The common duration forms: h:mm, h:mm:ss, 1h 30m, 90m, 45s, 1.5 -> seconds
    function parseDuration(raw) {
        const text = raw.trim().toLowerCase();
        if (!text) return null;
        let match;
        if ((match = text.match(/^(\d{1,2}):(\d{2})(?::(\d{2}))?$/))) {
            return +match[1] * 3600 + +match[2] * 60 + +(match[3] ?? 0);
        }
        if (/^\d*\.?\d+$/.test(text)) {
            const s = parseFloat(text) * 3600;
            return Number.isInteger(s) ? s : null;
        }
        if ((match = text.match(/^(?:(\d+)\s*h)?\s*(?:(\d+)\s*m)?\s*(?:(\d+)\s*s)?$/)) && (match[1] || match[2] || match[3])) {
            return (+match[1] || 0) * 3600 + (+match[2] || 0) * 60 + (+match[3] || 0);
        }
        return null;
    }

    // Mirrors ClockTime.format: "1:15 PM", "1:15:30 PM"
    function formatClock(secondOfDay) {
        const h = Math.floor(secondOfDay / 3600) % 24;
        const m = Math.floor(secondOfDay / 60) % 60;
        const s = secondOfDay % 60;
        let text = `${h % 12 === 0 ? 12 : h % 12}:${String(m).padStart(2, '0')}`;
        if (s) text += `:${String(s).padStart(2, '0')}`;
        return `${text} ${h < 12 ? 'AM' : 'PM'}`;
    }

    // Mirrors TimeInput.format: h:mm:ss
    const formatDuration = (seconds) =>
        `${Math.floor(seconds / 3600)}:${String(Math.floor(seconds / 60) % 60).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;

    function wire(form) {
        const start = form.elements.start;
        const end = form.elements.end;
        const duration = form.elements.duration;
        if (!start || !end || !duration) return;

        const read = () => {
            const s = parseClock(start.value);
            let e = parseClock(end.value);
            if (e === 0 && s !== null && s > 0) e = DAY;   // 12:00 AM as an end is the coming midnight
            return {s, e, d: parseDuration(duration.value)};
        };
        const flag = (input, bad) => input.toggleAttribute('aria-invalid', bad);

        start.addEventListener('change', () => {
            const {s, e, d} = read();
            flag(start, start.value.trim() !== '' && s === null);
            if (s === null) return;
            start.value = formatClock(s);
            if (e !== null && e > s) duration.value = formatDuration(e - s);
            else if (d !== null && s + d <= DAY) end.value = formatClock(s + d);
        });
        end.addEventListener('change', () => {
            const {s, e, d} = read();
            flag(end, end.value.trim() !== '' && e === null);
            if (e === null) return;
            end.value = formatClock(e);
            if (s !== null && e > s) duration.value = formatDuration(e - s);
            else if (s !== null && e === s) duration.value = '';
            else if (s === null && d !== null && e - d >= 0) start.value = formatClock(e - d);
        });
        duration.addEventListener('change', () => {
            const {s, e, d} = read();
            if (d === null) return;   // e.g. "9:00-10:30": the server reads that one
            duration.value = formatDuration(d);
            if (s !== null && s + d <= DAY) end.value = formatClock(s + d);
            else if (s === null && e !== null && e - d >= 0) start.value = formatClock(e - d);
        });

        // Clockify-style editing: on focus a time shows compactly and selected ("221PM"),
        // so typing replaces it; on blur it tidies back ("2:21 PM").
        [start, end].forEach((input) => {
            input.addEventListener('focus', () => {
                const s = parseClock(input.value);
                if (s !== null) input.value = formatClock(s).replace(/[\s:]/g, '');
                input.select();
            });
            input.addEventListener('blur', () => {
                const s = parseClock(input.value);
                if (s !== null) input.value = formatClock(s);
            });
        });

        // The entry bar opens on the current time, start = end, like Clockify, and keeps
        // up with the clock until you touch either box. (Edit rows keep the entry's times.)
        if (form.id === 'quick-add' && !start.value.trim() && !end.value.trim()) {
            let touched = false;
            [start, end].forEach((input) => input.addEventListener('input', () => { touched = true; }));
            const toNow = () => {
                if (touched || [start, end].includes(document.activeElement)) return;
                const now = new Date();
                start.value = end.value = formatClock(now.getHours() * 3600 + now.getMinutes() * 60);
            };
            toNow();
            setInterval(toNow, 15000);
        }
    }

    document.querySelectorAll('#quick-add, form.edit-entry').forEach(wire);
})();
