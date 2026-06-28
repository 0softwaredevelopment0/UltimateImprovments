"""
Telegram бот для 2FA Minecraft сервера @OakworldSRVbot.
При /start отвечает Chat ID.
При запросе от плагина отправляет inline кнопки Подтвердить/Отклонить.
Запуск: python bot.py
"""

import asyncio
import json
import uuid
from aiohttp import web
from aiogram import Bot, Dispatcher, types
from aiogram.filters import Command
from aiogram.types import InlineKeyboardMarkup, InlineKeyboardButton, CallbackQuery

API_TOKEN = "8770086137:AAHHv6ASBEeqHE2IxOmiq-HdC6xTD9R7D6g"
HTTP_PORT = 3000

bot = Bot(token=API_TOKEN)
dp = Dispatcher()

# Хранилище запросов: request_id -> {"status": "pending"|"approved"|"denied", "chat_id": ..., "player": ...}
confirm_requests = {}

# ========== TELEGRAM HANDLERS ==========

@dp.message(Command("start"))
async def cmd_start(message: types.Message):
    chat_id = message.chat.id
    await message.answer(
        f"🤖 Бот 2FA для Minecraft сервера\n\n"
        f"📌 Ваш Chat ID: <b>{chat_id}</b>\n\n"
        f"Введите этот ID на сервере:\n"
        f"<code>/mp auth 2fa setup {chat_id}</code>\n\n"
        f"После настройки, при входе на сервер\n"
        f"вам будет приходить запрос на подтверждение.",
        parse_mode="HTML"
    )


@dp.callback_query()
async def handle_callback(callback: CallbackQuery):
    data = json.loads(callback.data)
    request_id = data.get("request_id")
    action = data.get("action")  # "confirm" or "deny"

    if not request_id or request_id not in confirm_requests:
        await callback.answer("Этот запрос уже устарел или недействителен.", show_alert=True)
        return

    if confirm_requests[request_id]["status"] != "pending":
        await callback.answer("По этому запросу уже принято решение.", show_alert=True)
        return

    if action == "confirm":
        confirm_requests[request_id]["status"] = "approved"
        await callback.message.edit_text(
            f"✅ <b>Вход подтверждён</b>\n\n"
            f"Игрок: <code>{confirm_requests[request_id]['player']}</code>\n"
            f"Вы разрешили вход в аккаунт.",
            parse_mode="HTML"
        )
        await callback.answer("✅ Вход подтверждён!", show_alert=False)
    elif action == "deny":
        confirm_requests[request_id]["status"] = "denied"
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
    try:
        body = await request.json()
    except Exception:
        return web.json_response({"error": "invalid json"}, status=400)

    chat_id = str(body.get("chat_id"))
    player = body.get("player")
    if not chat_id or not player:
        return web.json_response({"error": "missing chat_id or player"}, status=400)

    req_id = str(uuid.uuid4())
    confirm_requests[req_id] = {
        "status": "pending",
        "chat_id": chat_id,
        "player": player,
        "created_at": asyncio.get_event_loop().time()
    }

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
        await bot.send_message(
            chat_id=chat_id,
            text=f"🔐 <b>Запрос на вход в аккаунт</b>\n\n"
                 f"Игрок: <code>{player}</code>\n"
                 f"IP: <code>{body.get('ip', '?')}</code>\n\n"
                 f"Это вы пытаетесь зайти на сервер?",
            parse_mode="HTML",
            reply_markup=keyboard
        )
        return web.json_response({"request_id": req_id, "status": "pending"})
    except Exception as e:
        del confirm_requests[req_id]
        return web.json_response({"error": str(e)}, status=500)


async def handle_check_confirm(request):
    """Плагин вызывает этот endpoint для проверки статуса подтверждения."""
    req_id = request.query.get("request_id")
    if not req_id or req_id not in confirm_requests:
        return web.json_response({"status": "not_found"})

    data = confirm_requests[req_id]
    # Очищаем устаревшие запросы (старше 5 минут)
    if data["status"] == "pending" and asyncio.get_event_loop().time() - data["created_at"] > 300:
        data["status"] = "timeout"

    return web.json_response({"status": data["status"], "player": data["player"]})


async def handle_stats(request):
    """Статистика: сколько активных запросов"""
    pending = sum(1 for r in confirm_requests.values() if r["status"] == "pending")
    return web.json_response({"pending_requests": pending, "total_requests": len(confirm_requests)})


# ========== WEB APP ==========

async def create_app():
    app = web.Application()
    app.router.add_post("/confirm-request", handle_confirm_request)
    app.router.add_get("/check-confirm", handle_check_confirm)
    app.router.add_get("/stats", handle_stats)
    return app


# ========== MAIN ==========

async def cleanup_old_requests():
    """Раз в 60 секунд удаляем устаревшие запросы (pending > 5 минут)."""
    while True:
        await asyncio.sleep(60)
        now = asyncio.get_event_loop().time()
        expired = [rid for rid, data in confirm_requests.items()
                   if data["status"] != "pending" and now - data["created_at"] > 600]
        for rid in expired:
            del confirm_requests[rid]


async def main():
    print("Bot started! https://t.me/OakworldSRVbot")
    print(f"HTTP server on port {HTTP_PORT}")

    web_app = await create_app()
    runner = web.AppRunner(web_app)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", HTTP_PORT)

    asyncio.create_task(cleanup_old_requests())

    await site.start()
    await dp.start_polling(bot)


if __name__ == "__main__":
    asyncio.run(main())
