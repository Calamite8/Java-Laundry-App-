SIMPLE LAUNDRY SYSTEM DASHBOARD - NetBeans Setup Guide
========================================================

This is a Java Swing desktop application (no database required —
data is stored in memory while the app runs). It has 4 tabs:

*** UPDATE: MainDashboard is now a real NetBeans JFrame Form ***
MainDashboard.java is now paired with MainDashboard.form. Opening the
project in NetBeans will show a "Design" button/tab for MainDashboard
in addition to "Source" — the outer window (the JTabbedPane container)
is editable in the drag-and-drop GUI Designer.

The content INSIDE each of the 4 tabs (tables, stat cards, forms) is
still built with plain Java code in buildTabs() and the createXxxPanel()
methods, not the designer. That's intentional, not a shortcut: those
tabs contain live JTables, computed stats, and listeners that Matisse's
static designer has no way to represent — the same reason real-world
NetBeans apps with dynamic data hand-code that part of the UI too.
If you drag new static components (buttons, labels) directly onto the
JFrame in Design view, that's fully supported; just note they'll sit
alongside the JTabbedPane, not inside a specific tab, unless you also
drag them onto the JTabbedPane in the designer.

  - Dashboard    : live stats (total orders, pending, in progress, revenue)
                    + a table of recent orders
  - New Order    : form to create an order for a customer
  - Orders       : table of all orders; change the Status dropdown
                    directly in the table (Pending -> Washing -> Drying
                    -> Folding -> Ready for Pickup -> Delivered)
  - Customers    : add / delete customers

Pricing (edit in Order.java if you want different rates):
  Wash & Fold : ₱50 / kilo
  Dry Clean   : ₱150 / item
  Iron Only   : ₱30 / item

--------------------------------------------------------
HOW TO OPEN THIS IN NETBEANS
--------------------------------------------------------

Option A — Create a new project and copy the files in (easiest):

1. Open NetBeans.
2. File > New Project > Java with Ant > Java Application.
   - Project Name: LaundrySystem
   - UNCHECK "Create Main Class" (we already have one).
   - Click Finish.
3. In the Projects panel, right-click the "Source Packages" node
   under LaundrySystem > New > Java Package, name it:
       laundrysystem
4. Copy all 6 files from the "src/laundrysystem" folder in this
   download into that new package folder ON DISK (easiest — file
   copy, not paste-into-editor, so MainDashboard.form comes along
   with MainDashboard.java and NetBeans recognizes it as a Form):
       Customer.java
       Order.java
       DataStore.java
       MainDashboard.java
       MainDashboard.form   <-- required for the Design view to work
       Main.java
   Then in NetBeans, right-click the laundrysystem package > Refresh.
5. Double-click MainDashboard.java in the Projects tree — you should
   now see "Source" and "Design" buttons at the top of the editor.
   Design view shows the JTabbedPane placeholder; Source view shows
   the generated initComponents() plus all the tab-building code.
6. Set the Main Class: Project Properties > Run > Main Class >
   laundrysystem.MainDashboard (or laundrysystem.Main — both work).
7. Press F6 (Run Project).

If NetBeans complains about the .form file when opening it (this can
happen if your NetBeans version's internal form schema differs
slightly), just delete MainDashboard.form — the app still compiles
and runs identically, you'll just lose the Design-view tab and edit
everything from Source view as before.

Option B — Open the folder directly:

1. Copy the entire "laundrysystem" folder from this download into
   your NetBeans projects directory.
2. In NetBeans: File > Open Project, browse to the folder, and open it
   (NetBeans will recognize the src/ layout once you've created a
   matching Ant project as in Option A — Java source folders on their
   own aren't a NetBeans project without the nbproject metadata, so
   Option A is the most reliable path).

--------------------------------------------------------
WHAT'S INCLUDED
--------------------------------------------------------
src/laundrysystem/Customer.java       - customer data model
src/laundrysystem/Order.java          - order data model + pricing logic
src/laundrysystem/DataStore.java      - in-memory storage (sample data seeded)
src/laundrysystem/MainDashboard.java  - JFrame Form: outer window + all 4 tabs
src/laundrysystem/MainDashboard.form  - NetBeans Designer metadata for the frame
src/laundrysystem/Main.java           - alternate application entry point

--------------------------------------------------------
CUSTOMIZING
--------------------------------------------------------
- Change prices: edit calculatePrice() in Order.java
- Add more statuses/services: edit the STATUSES / SERVICE_TYPES
  arrays at the top of Order.java
- Make data persist between runs: swap DataStore's ArrayLists for
  JDBC calls to a real database (e.g. SQLite or MySQL) — the rest
  of the UI code doesn't need to change since it only calls
  DataStore's methods.

Note: this was written and compile-tested with a standard Java
Swing setup (JDK 17+ recommended). It has no external dependencies,
so it will build in any NetBeans version that supports Java SE
(Ant-based "Java Application" project type).
