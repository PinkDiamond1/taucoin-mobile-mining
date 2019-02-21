package io.taucoin.android.wallet.module.view.main;

import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.mofei.tau.R;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnLongClick;
import io.taucoin.android.wallet.base.TransmitKey;
import io.taucoin.android.wallet.db.entity.TransactionHistory;
import io.taucoin.android.wallet.util.CopyManager;
import io.taucoin.android.wallet.util.DateUtil;
import io.taucoin.android.wallet.util.FmtMicrometer;
import io.taucoin.android.wallet.util.SharedPreferencesHelper;
import io.taucoin.android.wallet.util.ToastUtils;
import io.taucoin.foundation.util.StringUtil;

public class HistoryExpandableListAdapter extends BaseExpandableListAdapter {

    private List<TransactionHistory> historyList = new ArrayList<>();
    private String address;

    HistoryExpandableListAdapter() {
    }

    List<TransactionHistory> getData() {
        return historyList;
    }

    void setHistoryList(List<TransactionHistory> historyList, boolean isAdd) {
        address = SharedPreferencesHelper.getInstance().getString(TransmitKey.ADDRESS, "");
        if (!isAdd) {
            this.historyList.clear();
        }
        this.historyList.addAll(historyList);
        notifyDataSetChanged();
    }

    @Override
    public int getGroupCount() {
        return historyList.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return 1;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return historyList.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return historyList.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        GroupViewHolder groupViewHolder;
        TransactionHistory tx = historyList.get(groupPosition);
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_group, parent, false);
            groupViewHolder = new GroupViewHolder(convertView);
            convertView.setTag(groupViewHolder);
        } else {
            groupViewHolder = (GroupViewHolder) convertView.getTag();
        }
        groupViewHolder.viewLineBottom.setVisibility(isExpanded ? View.INVISIBLE : View.VISIBLE);
        groupViewHolder.viewLineTop.setVisibility(groupPosition != 0 ? View.INVISIBLE : View.VISIBLE);
        groupViewHolder.ivRight.setImageResource(isExpanded ? R.mipmap.icon_up : R.mipmap.icon_down);

        boolean isReceiver = isReceiver(tx);
        String amount = FmtMicrometer.fmtFormat(tx.getValue());
        amount = isReceiver ? "+" + amount : "-" + amount;
        groupViewHolder.tvAmount.setText(amount);

        String time = DateUtil.formatTime(tx.getTime(), DateUtil.pattern6);
        if(StringUtil.isEmpty(time) && tx.getBlocktime() > 0){
            time = DateUtil.formatTime(tx.getBlocktime(), DateUtil.pattern6);
        }
        groupViewHolder.tvTime.setText(time);
        // The user is the sender
        boolean isSuccess = StringUtil.isSame(TransmitKey.TxResult.SUCCESSFUL, tx.getResult());
        boolean isConfirming = StringUtil.isSame(TransmitKey.TxResult.CONFIRMING, tx.getResult());
        int color = R.color.color_red;
        if (isConfirming) {
            color = R.color.color_blue;
        } else if (isSuccess) {
            color = tx.getConfirmations() > TransmitKey.TX_CONFIRMATIONS ? R.color.color_black : R.color.color_blue;
        }

        if (StringUtil.isEmpty(tx.getResult())) {
            color = R.color.color_black;
        }
        int textColor = ContextCompat.getColor(parent.getContext(), color);
        groupViewHolder.tvAmount.setTextColor(textColor);
        groupViewHolder.tvTime.setTextColor(textColor);
        return convertView;
    }

    private boolean isReceiver(TransactionHistory tx) {
        boolean isReceiver = StringUtil.isNotSame(tx.getFromAddress(), address);
        // Only send to self handler
        if(!isReceiver && StringUtil.isSame(tx.getFromAddress(), tx.getToAddress())){
            isReceiver = StringUtil.isSame(tx.getSentOrReceived(), TransmitKey.TxType.RECEIVE);
        }
        return isReceiver;
    }

    @Override
    public View getChildView(final int groupPosition, int childPosition, boolean isLastChild, View
            convertView, ViewGroup parent) {
        ChildViewHolder childViewHolder;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_child, parent, false);
            childViewHolder = new ChildViewHolder(convertView);
            convertView.setTag(childViewHolder);
        } else {
            childViewHolder = (ChildViewHolder) convertView.getTag();
        }

        TransactionHistory tx = historyList.get(groupPosition);
        childViewHolder.tvTransactionId.setText(tx.getTxId());
        String fee = FmtMicrometer.fmtFormatFee(tx.getFee()) + "TAU";
        childViewHolder.tvTxFee.setText(fee);

        boolean isReceiver = isReceiver(tx);

        childViewHolder.tvAddressTitle.setText(isReceiver ? R.string.tx_from_address : R.string.tx_to_address);
        childViewHolder.tvReceivedAddress.setText(isReceiver ? tx.getFromAddress() : tx.getToAddress());
        childViewHolder.tvFeeTitle.setVisibility(isReceiver ? View.GONE : View.VISIBLE);
        childViewHolder.tvTxFee.setVisibility(isReceiver ? View.GONE : View.VISIBLE);

        childViewHolder.tvMemo.setText(tx.getMemo());
        boolean isHaveMemo = StringUtil.isNotEmpty(tx.getMemo());
        childViewHolder.tvMemo.setVisibility(!isHaveMemo  || isReceiver? View.GONE : View.VISIBLE);
        childViewHolder.tvMemoTitle.setVisibility(!isHaveMemo || isReceiver ? View.GONE : View.VISIBLE);

        childViewHolder.tvFailMsg.setText(tx.getMessage());
        boolean isHaveFailMsg = StringUtil.isSame(TransmitKey.TxResult.FAILED, tx.getResult()) && StringUtil.isNotEmpty(tx.getMessage());
        childViewHolder.tvFailMsg.setVisibility(isHaveFailMsg ? View.VISIBLE : View.GONE);
        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    class GroupViewHolder {
        @BindView(R.id.tv_amount)
        TextView tvAmount;
        @BindView(R.id.tv_time)
        TextView tvTime;
        @BindView(R.id.iv_right)
        ImageView ivRight;
        @BindView(R.id.view_line_top)
        View viewLineTop;
        @BindView(R.id.view_line_bottom)
        View viewLineBottom;

        GroupViewHolder(View view) {
            ButterKnife.bind(this, view);
        }
    }

    class ChildViewHolder {
        @BindView(R.id.tv_received_address)
        TextView tvReceivedAddress;
        @BindView(R.id.tv_transaction_id)
        TextView tvTransactionId;
        @BindView(R.id.tv_tx_fee)
        TextView tvTxFee;
        @BindView(R.id.tv_fail_msg)
        TextView tvFailMsg;
        @BindView(R.id.tv_address_title)
        TextView tvAddressTitle;
        @BindView(R.id.tv_fee_title)
        TextView tvFeeTitle;
        @BindView(R.id.tv_memo_title)
        TextView tvMemoTitle;
        @BindView(R.id.tv_memo)
        TextView tvMemo;

        ChildViewHolder(View view) {
            ButterKnife.bind(this, view);
        }

        @OnLongClick(R.id.tv_transaction_id)
        boolean copyTransactionId() {
            CopyManager.copyText(StringUtil.getText(tvTransactionId));
            ToastUtils.showShortToast(R.string.tx_id_copy);
            return false;
        }

        @OnLongClick(R.id.tv_received_address)
        boolean copyAddress() {
            CopyManager.copyText(StringUtil.getText(tvReceivedAddress));
            ToastUtils.showShortToast(R.string.tx_address_copy);
            return false;
        }
    }
}