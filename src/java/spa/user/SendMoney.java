/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package spa.user;

import spa.Account;
import spa.Attachment;
import spa.Constants;
import spa.Spa;
import spa.SpaException;
import spa.Transaction;
import spa.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static spa.user.JSONResponses.NOTIFY_OF_ACCEPTED_TRANSACTION;

public final class SendMoney extends UserServlet.UserRequestHandler {

    static final SendMoney instance = new SendMoney();

    private SendMoney() {}

    @Override
    JSONStreamAware processRequest(HttpServletRequest req, User user) throws SpaException.ValidationException, IOException {
        if (user.getSecretPhrase() == null) {
            return null;
        }

        String recipientValue = req.getParameter("recipient");
        String amountValue = req.getParameter("amountSPA");
        String feeValue = req.getParameter("feeSPA");
        String deadlineValue = req.getParameter("deadline");
        String secretPhrase = req.getParameter("secretPhrase");

        long recipient;
        long amountAPL = 0;
        long feeAPL = 0;
        short deadline = 0;

        try {

            recipient = Convert.parseUnsignedLong(recipientValue);
            if (recipient == 0) throw new IllegalArgumentException("invalid recipient");
            amountAPL = Convert.parseSPA(amountValue.trim());
            feeAPL = Convert.parseSPA(feeValue.trim());
            deadline = (short)(Double.parseDouble(deadlineValue) * 60);

        } catch (RuntimeException e) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "One of the fields is filled incorrectly!");
            response.put("recipient", recipientValue);
            response.put("amountSPA", amountValue);
            response.put("feeSPA", feeValue);
            response.put("deadline", deadlineValue);

            return response;
        }

        if (! user.getSecretPhrase().equals(secretPhrase)) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "Wrong secret phrase!");
            response.put("recipient", recipientValue);
            response.put("amountSPA", amountValue);
            response.put("feeSPA", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else if (amountAPL <= 0 || amountAPL > Constants.MAX_BALANCE_APL) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "\"Amount\" must be greater than 0!");
            response.put("recipient", recipientValue);
            response.put("amountSPA", amountValue);
            response.put("feeSPA", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else if (feeAPL < Constants.ONE_SPA || feeAPL > Constants.MAX_BALANCE_APL) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "\"Fee\" must be at least 1 SPA!");
            response.put("recipient", recipientValue);
            response.put("amountSPA", amountValue);
            response.put("feeSPA", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else if (deadline < 1 || deadline > 1440) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "\"Deadline\" must be greater or equal to 1 minute and less than 24 hours!");
            response.put("recipient", recipientValue);
            response.put("amountSPA", amountValue);
            response.put("feeSPA", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        }

        Account account = Account.getAccount(user.getPublicKey());
        if (account == null || Math.addExact(amountAPL, feeAPL) > account.getUnconfirmedBalanceAPL()) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "Not enough funds!");
            response.put("recipient", recipientValue);
            response.put("amountSPA", amountValue);
            response.put("feeSPA", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else {

            final Transaction transaction = Spa.newTransactionBuilder(user.getPublicKey(),
                    amountAPL, feeAPL, deadline, Attachment.ORDINARY_PAYMENT).recipientId(recipient).build(secretPhrase);

            Spa.getTransactionProcessor().broadcast(transaction);

            return NOTIFY_OF_ACCEPTED_TRANSACTION;

        }
    }

    @Override
    boolean requirePost() {
        return true;
    }

}
