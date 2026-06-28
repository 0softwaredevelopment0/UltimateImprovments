"""
Telegram бот для 2FA Minecraft сервера @OakworldSRVbot.
При /start отвечает Chat ID.
При запросе от плагина отправляет inline кнопки Подтвердить/Отклонить.
Запуск: python bot.py

Логи пишутся в bot.log и в консоль.
"""

import asyncio
import json
import uuid
import logging
import sys
import os

# ========== ЛОГИРОВАНИЕ ==========
logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler("bot.log"),
        logging.StreamHandler(sys.stdout)
    ]
)
log = logging.getLogger("bot")

log.info("=== BOT STARTING ===")
log.info(f"Python version: {sys.version}")
log.info(f"PID: {os.getpid()}")

try:
    from aiohttp import web
    log.info("[OK] aiohttp imported")
except Exception as e:
    log.error(f"[FAIL] Failed to import aiohttp: {e}")

try:
    from aiogram import Bot, Dispatcher, types
    from aiogram.filters import Command
    from aiogram.types import InlineKeyboardMarkup, InlineKeyboardButton, CallbackQuery
    log.info("[OK] aiogram imported")
except Exception as e:
    log.error(f"[FAIL] Failed to import aiogram: {e}")

API_TOKEN = "8770086137:AAHHv6ASBEeqHE2IxOmiq-HdC6xTD9R7D6g"
HTTP_PORT = 3000

log.info("Creating Bot instance...")
bot = Bot(token=API_TOKEN)
dp = Dispatcher()
log.info("[OK] Bot + Dispatcher created")

# Хранилище запросов: request_id -> {"status": "pending"|"approved"|"denied", "chat_id": ..., "player": ...}
confirm_requests = {}

# ========== TELEGRAM HANDLERS ==========

@dp.message(Command("start"))
async def cmd_start(message: types.Message):
    chat_id = message.chat.id
    log.info(f"/start from chat_id={chat_id} username={message.from_user.username}")
    await message.answer(
        f"🤖 Бот 2FA для Minecraft сервера\n\n"
        f"📌 Ваш Chat ID: <b>{chat_id}</b>\n\n"
        f"Введите этот ID на сервере:\n"
        f"<code>/mp auth 2fa setup {chat_id}</code>\n\n"
        f"После настройки, при входе на сервер\n"
        f"вам будет приходить запрос на подтверждение.",
        parse_mode="HTML"
    )
    log.info(f"/start ответ отправлен chat_id={chat_id}")


@dp.callback_query()
async def handle_callback(callback: CallbackQuery):
    log.info(f"Callback received: {callback.data}")
    try:
        data = json.loads(callback.data)
    except json.JSONDecodeError as e:
        log.error(f"JSON decode error: {e}")
        await callback.answer("Ошибка данных", show_alert=True)
        return

    request_id = data.get("request_id")
    action = data.get("action")  # "confirm" or "deny"

    if not request_id or request_id not in confirm_requests:
        log.warning(f"Request {request_id} not found or expired")
        await callback.answer("Этот запрос уже устарел или недействителен.", show_alert=True)
        return

    if confirm_requests[request_id]["status"] != "pending":
        log.info(f"Request {request_id} already processed: {confirm_requests[request_id]['status']}")
        await callback.answer("По этому запросу уже принято решение.", show_alert=True)
        return

    if action == "confirm":
        confirm_requests[request_id]["status"] = "approved"
        log.info(f"Request {request_id} APPROVED by user")
        await callback.message.edit_text(
            f"✅ <b>Вход подтверждён</b>\n\n"
            f"Игрок: <code>{confirm_requests[request_id]['player']}</code>\n"
            f"Вы разрешили вход в аккаунт.",
            parse_mode="HTML"
        )
        await callback.answer("✅ Вход подтверждён!", show_alert=False)
    elif action == "deny":
        confirm_requests[request_id]["status"] = "denied"
        log.info(f"Request {request_id} DENIED by user")
        await callback.message.edit_text(
            f"❌ <b>Вход отклонён</b>\n\n"
            f"Игрок: <code>{confirm_requests[request_id]['player']}</code>\n"
            f"Вы запретили вход в аккаунт.",
            parse_mode="HTML"
        )
        await callback.answer("❌ Вход отклонён!", show_alert=False)

# ========== HTTP ENDPOINTS ==========

async def handle_confirm_request(request):
    """Плагин вызывает этот endpoint для отправки запроса на подтверждение."""
    log.info("HTTP POST /confirm-request received")
    try:
        body = await request.json()
        log.info(f"Body: chat_id={body.get('chat_id')}, player={body.get('player')}, ip={body.get('ip')}")
    except Exception as e:
        log.error(f"JSON parse error: {e}")
        return web.json_response({"error": "invalid json"}, status=400)

    chat_id = str(body.get("chat_id"))
    player = body.get("player")
    if not chat_id or not player:
        log.warning(f"Missing fields: chat_id={chat_id}, player={player}")
        return web.json_response({"error": "missing chat_id or player"}, status=400)

    req_id = str(uuid.uuid4())
    confirm_requests[req_id] = {
        "status": "pending",
        "chat_id": chat_id,
        "player": player,
        "created_at": asyncio.get_event_loop().time()
    }
    log.info(f"Created request {req_id} for player={player} chat_id={chat_id}")

    # Отправляем inline клавиатуру
    keyboard = InlineKeyboardMarkup(inline_keyboard=[
        [
            InlineKeyboardButton(
                text="✅ Подтвердить",
                callback_data=json.dumps({"request_id": req_id, "action": "confirm"})
            ),
            InlineKeyboardButton(
                text="❌ Отклонить",
                callback_data=json.dumps({"request_id": req_id, "action": "deny"})
            )
        ]
    ])

    try:
        log.info(f"Sending Telegram message to chat_id={chat_id}...")
        await bot.send_message(
            chat_id=chat_id,
            text=f"🔐 <b>Запрос на вход в аккаунт</b>\n\n"
                 f"Игрок: <code>{player}</code>\n"
                 f"IP: <code>{body.get('ip', '?')}</code>\n\n"
                 f"Это вы пытаетесь зайти на сервер?",
            parse_mode="HTML",
            reply_markup=keyboard
        )
        log.info(f"Telegram message sent successfully to {chat_id}")
        return web.json_response({"request_id": req_id, "status": "pending"})
    except Exception as e:
        log.error(f"Failed to send Telegram message: {e}")
        del confirm_requests[req_id]
        return web.json_response({"error": str(e)}, status=500)


async def handle_check_confirm(request):
    """Плагин вызывает этот endpoint для проверки статуса подтверждения."""
    req_id = request.query.get("request_id")
    log.info(f"HTTP GET /check-confirm?request_id={req_id}")

    if not req_id or req_id not in confirm_requests:
        log.info(f"Request {req_id} not found")
        return web.json_response({"status": "not_found"})

    data = confirm_requests[req_id]
    # Очищаем устаревшие запросы (старше 5 минут)
    if data["status"] == "pending" and asyncio.get_event_loop().time() - data["created_at"] > 300:
        data["status"] = "timeout"
        log.info(f"Request {req_id} timed out")

    log.info(f"Request {req_id} status: {data['status']}")
    return web.json_response({"status": data["status"], "player": data["player"]})


async def handle_stats(request):
    """Статистика: сколько активных запросов"""
    pending = sum(1 for r in confirm_requests.values() if r["status"] == "pending")
    total = len(confirm_requests)
    log.info(f"Stats: pending={pending}, total={total}")
    return web.json_response({"pending_requests": pending, "total_requests": total})

# ========== WEB APP ==========

async def create_app():
    log.info("Creating web application...")
    app = web.Application()
    app.router.add_post("/confirm-request", handle_confirm_request)
    app.router.add_get("/check-confirm", handle_check_confirm)
    app.router.add_get("/stats", handle_stats)
    log.info("[OK] Routes: POST /confirm-request, GET /check-confirm, GET /stats")
    return app


# ========== MAIN ==========

async def cleanup_old_requests():
    """Раз в 60 секунд удаляем устаревшие запросы (pending > 5 минут)."""
    log.info("Cleanup task started")
    while True:
        await asyncio.sleep(60)
        now = asyncio.get_event_loop().time()
        expired = [rid for rid, data in confirm_requests.items()
                   if data["status"] != "pending" and now - data["created_at"] > 600]
        for rid in expired:
            del confirm_requests[rid]
        if expired:
            log.info(f"Cleaned up {len(expired)} expired requests")


async def main():
    log.info("Running bot! https://t.me/OakworldSRVbot")
    log.info(f"HTTP server on port {HTTP_PORT}")

    web_app = await create_app()
    runner = web.AppRunner(web_app)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", HTTP_PORT)

    asyncio.create_task(cleanup_old_requests())

    await site.start()
    log.info(f"[OK] HTTP server started on 0.0.0.0:{HTTP_PORT}")
    log.info("Starting Telegram polling...")
    await dp.start_polling(bot)


if __name__ == "__main__":
    log.info("=== __main__ ===")
    try:
        asyncio.run(main())
    except Exception as e:
        log.error(f"FATAL ERROR: {e}", exc_info=True)
    log.info("=== BOT EXITED ===")
