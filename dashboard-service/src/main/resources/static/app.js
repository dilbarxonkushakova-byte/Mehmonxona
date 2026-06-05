/* HotelOS Dashboard — front-end controller
 * Backend endpoints (see Spring services):
 *   Reception     :8081  /api/reception/{rooms, bookings, checkout/{roomNumber}}
 *   Housekeeping  :8082  /api/housekeeping/{queue, clean/{room}}
 *   Room service  :8083  /api/roomservice/{menu, orders, orders/{id}/deliver}
 *   Maintenance   :8084  /api/maintenance/{tickets, tickets/{id}/resolve}
 */

const API = {
  RECEPTION:    "http://localhost:8081/api/reception",
  HOUSEKEEPING: "http://localhost:8082/api/housekeeping",
  ROOM_SERVICE: "http://localhost:8083/api/roomservice",
  MAINTENANCE:  "http://localhost:8084/api/maintenance",
};

// ---------- shared fetch helper ----------
async function api(url, method = "GET", body = null) {
  const opts = { method, headers: { "Content-Type": "application/json" } };
  if (body !== null) opts.body = JSON.stringify(body);

  const res = await fetch(url, opts);
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`${res.status} ${text}`);
  }
  // tolerate empty responses
  const txt = await res.text();
  return txt ? JSON.parse(txt) : null;
}

function toast(msg, isError = false) {
  const el = document.getElementById("toast");
  if (!el) return;
  el.textContent = msg;
  el.className = "toast show" + (isError ? " error" : "");
  setTimeout(() => (el.className = "toast"), 2500);
}

// ---------- state ----------
let selectedBeds = 1;

// ====================================================================
// RECEPTION
// ====================================================================
async function loadRooms() {
  try {
    const rooms = await api(`${API.RECEPTION}/rooms`);

    const container = document.getElementById("roomsContainer");
    container.innerHTML = "";

    let dirty = 0;

    rooms.forEach(room => {
      if (room.status === "DIRTY") dirty++;

      const card = document.createElement("div");
      card.className = `room-card ${room.status.toLowerCase()}`;

      const canBook = room.status === "READY";

      card.innerHTML = `
        <h3>Room ${room.number}</h3>
        <p>Floor: ${room.floor} · Beds: ${room.beds}</p>
        <p>Rate: $${room.nightlyRate}/night</p>
        <p>Status: <strong>${room.status}</strong></p>
        <div class="room-actions">
          <button ${canBook ? "" : "disabled"} data-action="book"
                  data-beds="${room.beds}">Book</button>
          <button ${room.status === "OCCUPIED" ? "" : "disabled"}
                  data-action="checkout"
                  data-room="${room.number}">Check Out</button>
        </div>
      `;

      card.querySelector('[data-action="book"]').addEventListener("click", () =>
        openBookingModal(room.beds));
      card.querySelector('[data-action="checkout"]').addEventListener("click", () =>
        checkOut(room.number));

      container.appendChild(card);
    });

    document.getElementById("roomCount").innerText  = `Rooms: ${rooms.length}`;
    document.getElementById("dirtyCount").innerText = `Dirty: ${dirty}`;
  } catch (err) {
    console.error("Rooms error:", err);
    toast("Failed to load rooms", true);
  }
}
/*
function openBookingModal(beds) {
  selectedBeds = beds;
  document.getElementById("selectedBeds").value = beds;
  document.getElementById("bookingModal").classList.add("open");
}
*/
function openBookingModal(beds) {
    console.log("BOOK CLICKED", beds);

    selectedBeds = beds;

    const bedsInput = document.getElementById("selectedBeds");
    console.log("selectedBeds input =", bedsInput);

    const modal = document.getElementById("bookingModal");
    console.log("bookingModal =", modal);

    if (bedsInput) {
        bedsInput.value = beds;
    }

    if (modal) {
        modal.classList.add("open");
    }
}

function closeModal() {
  document.getElementById("bookingModal").classList.remove("open");
}

function toInstant(localValue) {
  // datetime-local => "2026-06-05T14:30"; backend wants ISO-8601 with offset
  if (!localValue) return null;
  return new Date(localValue).toISOString();
}

async function confirmBooking() {
  const guestId  = document.getElementById("guestId").value.trim();
  const checkIn  = toInstant(document.getElementById("checkIn").value);
  const checkOut = toInstant(document.getElementById("checkOut").value);

  if (!guestId || !checkIn || !checkOut) {
    toast("Please fill all booking fields", true);
    return;
  }

  try {
    const res = await api(`${API.RECEPTION}/bookings`, "POST", {
      guestId,
      beds: selectedBeds,
      checkIn,
      checkOut,
    });
    toast(`Booked room ${res.roomNumber}`);
    closeModal();
    loadRooms();
  } catch (err) {
    console.error("Booking error:", err);
    toast("Booking failed: " + err.message, true);
  }
}

async function checkOut(roomNumber) {
  try {
    const res = await api(`${API.RECEPTION}/checkout/${roomNumber}`, "POST");
    toast(`Checked out room ${roomNumber}. Total: $${res.total}`);
    loadRooms();
    loadHousekeeping();
  } catch (err) {
    console.error(err);
    toast("Checkout failed: " + err.message, true);
  }
}

// ====================================================================
// HOUSEKEEPING — backend returns List<Integer> (room numbers)
// ====================================================================
async function loadHousekeeping() {
  try {
    const queue = await api(`${API.HOUSEKEEPING}/queue`);

    const tbody = document.getElementById("housekeepingTable");
    tbody.innerHTML = "";

    queue.forEach(roomNumber => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${roomNumber}</td>
        <td>DIRTY</td>
        <td><button data-room="${roomNumber}">Mark Clean</button></td>
      `;
      tr.querySelector("button").addEventListener("click", () => markClean(roomNumber));
      tbody.appendChild(tr);
    });
  } catch (err) {
    console.error(err);
    toast("Failed to load housekeeping", true);
  }
}

async function markClean(roomNumber) {
  try {
    await api(`${API.HOUSEKEEPING}/clean/${roomNumber}?cleaner=front-desk`, "POST");
    toast(`Room ${roomNumber} cleaned`);
    loadHousekeeping();
    loadRooms();
  } catch (err) {
    toast("Failed: " + err.message, true);
  }
}

// ====================================================================
// ROOM SERVICE
// ====================================================================
async function loadMenu() {
  try {
    const menu = await api(`${API.ROOM_SERVICE}/menu`);
    const sel = document.getElementById("menuSelect");
    sel.innerHTML = "";
    Object.entries(menu).forEach(([item, price]) => {
      const opt = document.createElement("option");
      opt.value = item;
      opt.textContent = `${item} ($${price})`;
      sel.appendChild(opt);
    });
  } catch (err) {
    console.error("Menu error:", err);
  }
}

async function loadOrders() {
  try {
    const orders = await api(`${API.ROOM_SERVICE}/orders`);

    const tbody = document.getElementById("ordersTable");
    tbody.innerHTML = "";

    orders.forEach(o => {
      const tr = document.createElement("tr");
      const status = o.delivered ? "DELIVERED" : "PENDING";
      tr.innerHTML = `
        <td>${o.id.substring(0, 8)}</td>
        <td>${o.roomNumber}</td>
        <td>${o.item} ($${o.price})</td>
        <td>${status}</td>
        <td>${o.delivered ? "" : `<button data-id="${o.id}">Done</button>`}</td>
      `;
      const btn = tr.querySelector("button");
      if (btn) btn.addEventListener("click", () => deliverOrder(o.id));
      tbody.appendChild(tr);
    });

    document.getElementById("orderCount").innerText = `Orders: ${orders.length}`;
  } catch (err) {
    console.error(err);
    toast("Failed to load orders", true);
  }
}

async function placeOrder() {
  const roomNumber = parseInt(document.getElementById("orderRoomNumber").value, 10);
  const item = document.getElementById("menuSelect").value;

  if (!roomNumber || !item) {
    toast("Choose room number and item", true);
    return;
  }

  try {
    await api(`${API.ROOM_SERVICE}/orders`, "POST", { roomNumber, item });
    toast("Order placed");
    loadOrders();
  } catch (err) {
    toast("Order failed: " + err.message, true);
  }
}

async function deliverOrder(id) {
  try {
    await api(`${API.ROOM_SERVICE}/orders/${id}/deliver`, "POST");
    loadOrders();
  } catch (err) {
    toast("Failed: " + err.message, true);
  }
}

// ====================================================================
// MAINTENANCE
// ====================================================================
async function loadTickets() {
  try {
    const tickets = await api(`${API.MAINTENANCE}/tickets`);

    const tbody = document.getElementById("ticketsTable");
    tbody.innerHTML = "";

    document.getElementById("ticketCount").innerText = `Tickets: ${tickets.length}`;

    tickets.forEach(t => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${t.id.substring(0, 8)}</td>
        <td>${t.roomNumber}</td>
        <td>${t.issue}</td>
        <td>${t.urgency}</td>
        <td>OPEN</td>
        <td><button data-id="${t.id}">Resolve</button></td>
      `;
      tr.querySelector("button").addEventListener("click", () => resolveTicket(t.id));
      tbody.appendChild(tr);
    });
  } catch (err) {
    console.error(err);
    toast("Failed to load tickets", true);
  }
}

async function reportIssue() {
  const roomNumber = parseInt(document.getElementById("ticketRoomNumber").value, 10);
  const issue = document.getElementById("ticketIssue").value.trim();
  const urgency = parseInt(document.getElementById("ticketUrgency").value, 10);

  if (!roomNumber || !issue || !urgency) {
    toast("Fill all ticket fields", true);
    return;
  }
  if (urgency < 1 || urgency > 4) {
    toast("Urgency must be 1..4", true);
    return;
  }

  try {
    await api(`${API.MAINTENANCE}/tickets`, "POST", { roomNumber, issue, urgency });
    toast("Issue reported");
    loadTickets();
  } catch (err) {
    toast("Report failed: " + err.message, true);
  }
}

async function resolveTicket(id) {
  try {
    await api(`${API.MAINTENANCE}/tickets/${id}/resolve`, "POST");
    loadTickets();
  } catch (err) {
    toast("Failed: " + err.message, true);
  }
}

// ====================================================================
// NAV
// ====================================================================
function showSection(sectionId) {
  // HTML buttons pass the full id (e.g. "receptionSection")
  document.querySelectorAll(".section").forEach(s => s.classList.remove("active"));
  const el = document.getElementById(sectionId);
  if (el) el.classList.add("active");
}

// ====================================================================
// INIT
// ====================================================================
window.addEventListener("DOMContentLoaded", () => {
  loadRooms();
  loadHousekeeping();
  loadMenu();
  loadOrders();
  loadTickets();

  // refresh every 10s
  setInterval(() => {
    loadRooms();
    loadHousekeeping();
    loadOrders();
    loadTickets();
  }, 10000);
});

// Expose handlers used by inline onclick="" in index.html
Object.assign(window, {
  showSection, loadRooms, loadHousekeeping, loadOrders, loadTickets,
  placeOrder, reportIssue, confirmBooking, closeModal,
});
