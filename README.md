# Java-Laundry-App-
Simple Laundry System Dashboard ni Vince at Amer (Login Form)
User: LaundryShopStaff
Pass: admin11


1. Asan yung file?
A: nasa LaundrySystem > target > start.bat; hindi yan exe file kase java yan.
2. Pag tapos mag log in, Kapag Matagal mag loading ng Dashboard, nag hahanap lang yan ng mySQL database sa Local machine, hintayin nyo.
3. Pag lumabas na yung main dashboard punta ka sa settings at enable mo yung Disable mySQL (For Testing) at save data locally to a file (Kapag walang mySQL workbench ang local machine).
4. Ano yung yung username at password?
A: Tanong nyo kay amer.
5. Bakit di makita yung status at claim status?
A: Naka shut down yung PC ni Vince
6. Diba Java lang ang na include nyo sa presentation? bakit may web redirection?
A: it is an implementation of a Server-Side Rendering (SSR) function or Java-in-HTML string concatenation, technically it is still in java.
-------------------------------------------------------------------------------------------------------------------------------------------
"<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                    + "<title>Order Status</title>"
                    + "<style>body{font-family:sans-serif;max-width:420px;margin:40px auto;padding:0 20px;color:#333;}"
                    + "h1{font-size:20px;} .status{font-size:28px;font-weight:bold;color:#2980b9;margin:10px 0;}"
                    + "table{width:100%;border-collapse:collapse;margin-top:15px;}"
                    + "td{padding:8px 0;border-bottom:1px solid #eee;} td:first-child{color:#888;}</style>"
                    + "</head><body>"
                    + "<h1>Order #" + order.id + "</h1>"
                    + "<div class=\"status\">" + escape(order.status) + "</div>"
                    + claimedNote
                    + "<table>"
                    + "<tr><td>Customer</td><td>" + escape(order.customerName) + "</td></tr>"
                    + "<tr><td>Service</td><td>" + escape(order.serviceType) + "</td></tr>"
                    + "<tr><td>Total</td><td>\u20B1" + String.format("%.2f", order.price) + "</td></tr>"
                    + "</table>"
                    + "</body></html>";
                    
---------------------------------------------------------------------------------------------------------------------------------------------

8. Ang gulo
A: Goodluck.
