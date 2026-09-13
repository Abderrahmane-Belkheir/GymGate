# Real software screenshots

Drop real screenshots in here using these exact filenames, and each spot on
the site switches from its illustrative diagram to the real screen
automatically — no code changes needed, `Screenshot.tsx` checks for each
file at runtime. All of this is wired up in `SoftwareTour.tsx` (the
screen-by-screen walkthrough) and `OwnerExperience.tsx` (the mobile app).

| File | Screen | Shows |
|---|---|---|
| `home-main.png` | Home | The main check-in screen — camera feed, recognition result, today's stats |
| `home-register.png` | Home | The "Register member" dialog |
| `home-seance.png` | Home | The single-session ("séance") sale dialog |
| `members-list.png` | Members | The members table/list |
| `member-detail.png` | Members | A member's detail view (history, payments, visits) |
| `plans.png` | Plans | The plans screen, including single-session pricing |
| `plans-create.png` | Plans | The "Create plan" dialog |
| `attendance.png` | Attendance | The attendance log/calendar view |
| `attendance-stats.png` | Attendance | The attendance trend chart (same idea as the payments chart) |
| `payments-list.png` | Payments | The payments list view |
| `payments-stats.png` | Payments | The payments statistics/trend view |
| `reports.png` | Reports | The reports screen |
| `owner-app.png` | Owner's app | A screen from the mobile app (framed as a phone) |

Add more later the same way: give the file a name, add one `<Screenshot src=… />`
call where you want it, and (for the tour) one entry in that screen's `shots`
array in `SoftwareTour.tsx` — a screen can have more than one screenshot, as
Home, Members, Plans, Attendance and Payments already do.

**Format:** PNG or JPG.
- Desktop screens (everything except `owner-app`) are framed in a plain
  window — a clean crop of just the app window (no OS taskbar/desktop around
  it) looks best, ideally ~1200–1600px wide.
- The phone screen (`owner-app`) is framed in a phone bezel — crop tight to
  the app's own screen, portrait orientation, ideally ~750–900px wide.
